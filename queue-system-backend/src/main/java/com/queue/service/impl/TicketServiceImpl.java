package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.queue.common.BusinessException;
import com.queue.common.ResultCode;
import com.queue.dto.ActiveTicketResponse;
import com.queue.dto.AdminTicketVO;
import com.queue.dto.CancelTicketRequest;
import com.queue.dto.MyTicketVO;
import com.queue.dto.QueueStatusResponse;
import com.queue.dto.TakeTicketRequest;
import com.queue.dto.TakeTicketResponse;
import com.queue.entity.BusinessType;
import com.queue.entity.Counter;
import com.queue.entity.Region;
import com.queue.entity.RegionBusiness;
import com.queue.entity.Ticket;
import com.queue.enums.TicketSource;
import com.queue.enums.TicketStatus;
import com.queue.mapper.BusinessTypeMapper;
import com.queue.mapper.CounterMapper;
import com.queue.mapper.RegionBusinessMapper;
import com.queue.mapper.RegionMapper;
import com.queue.mapper.TicketMapper;
import com.queue.service.QueueService;
import com.queue.service.TicketService;
import com.queue.util.PhoneUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TicketServiceImpl implements TicketService {

    private final TicketMapper ticketMapper;
    private final BusinessTypeMapper businessTypeMapper;
    private final CounterMapper counterMapper;
    private final RegionMapper regionMapper;
    private final RegionBusinessMapper regionBusinessMapper;
    private final QueueService queueService;
    private final StringRedisTemplate stringRedisTemplate;

    public TicketServiceImpl(TicketMapper ticketMapper, BusinessTypeMapper businessTypeMapper, CounterMapper counterMapper, RegionMapper regionMapper, RegionBusinessMapper regionBusinessMapper, QueueService queueService, StringRedisTemplate stringRedisTemplate) {
        this.ticketMapper = ticketMapper;
        this.businessTypeMapper = businessTypeMapper;
        this.counterMapper = counterMapper;
        this.regionMapper = regionMapper;
        this.regionBusinessMapper = regionBusinessMapper;
        this.queueService = queueService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    @Transactional
    public TakeTicketResponse takeTicket(TakeTicketRequest request) {
        // 1. Validate business type exists and is globally enabled
        BusinessType bt = businessTypeMapper.selectById(request.getBusinessTypeId());
        if (bt == null || bt.getIsEnabled() == null || !bt.getIsEnabled()) {
            throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE);
        }

        // 如果没有传regionId，则从区域-业务关联中查找（使用第一个启用该业务的区域）
        Long regionId = request.getRegionId();
        if (regionId == null) {
            List<RegionBusiness> rbList = regionBusinessMapper.selectList(
                new QueryWrapper<RegionBusiness>()
                    .eq("business_type_id", request.getBusinessTypeId())
                    .eq("is_enabled", 1)
                    .last("LIMIT 1")
            );
            if (!rbList.isEmpty()) {
                regionId = rbList.get(0).getRegionId();
            }
        }

        if (regionId == null) {
            throw new BusinessException(50001, "该业务类型在当前区域不可用");
        }

        // 查询区域代码，用于生成含区域标识的唯一票号
        Region region = regionMapper.selectById(regionId);
        if (region == null || region.getRegionCode() == null) {
            throw new BusinessException(50001, "区域信息不完整，无法生成票号");
        }
        String regionCode = region.getRegionCode();

        // 校验该区域是否启用了此业务类型
        RegionBusiness rb = regionBusinessMapper.selectOne(
            new QueryWrapper<RegionBusiness>()
                .eq("region_id", regionId)
                .eq("business_type_id", request.getBusinessTypeId())
        );
        if (rb == null || !Boolean.TRUE.equals(rb.getIsEnabled())) {
            throw new BusinessException(50001, "该业务类型在当前区域未启用");
        }

        // 2. Check duplicate ticket with distributed lock
        String maskedPhone = PhoneUtil.mask(request.getPhone());
        String lockKey = "queue:lock:dup:" + regionId + ":" + request.getBusinessTypeId() + ":" + maskedPhone;
        boolean locked = queueService.acquireLock(lockKey, 5);
        if (!locked) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }

        String ticketNo = null;
        Ticket ticket = null;
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1);

            // 查找当日同手机号+同业务类型的所有记录（含已办结）
            List<Ticket> todayTickets = ticketMapper.selectList(
                new QueryWrapper<Ticket>()
                    .eq("phone", maskedPhone)
                    .eq("business_type_id", request.getBusinessTypeId())
                    .ge("created_at", startOfDay)
                    .lt("created_at", endOfDay)
            );

            if (!todayTickets.isEmpty()) {
                // 找出未办结的票（WAITING/CALLED/SERVING）
                List<Ticket> unfinished = todayTickets.stream()
                    .filter(t -> {
                        String s = t.getStatus();
                        return TicketStatus.WAITING.getValue().equals(s)
                            || TicketStatus.CALLED.getValue().equals(s)
                            || TicketStatus.SERVING.getValue().equals(s);
                    })
                    .toList();

                if (!unfinished.isEmpty()) {
                    // 存在未办结票：姓名必须一致
                    String firstName = unfinished.get(0).getName();
                    if (request.getName() != null && !request.getName().equals(firstName)) {
                        throw new BusinessException(ResultCode.DUPLICATE_TICKET);
                    }
                    // 姓名一致，禁止重复取号
                    throw new BusinessException(ResultCode.DUPLICATE_TICKET);
                }

                // 无未办结票（同手机号+业务类型今日已全部办结）：姓名必须与首张票一致
                String firstName = todayTickets.get(0).getName();
                if (request.getName() != null && !request.getName().equals(firstName)) {
                    throw new BusinessException(50001, "该手机号今日已使用其他姓名取号，无法使用不同姓名取号");
                }
            }

            // 3. Generate sequence and ticket number
            // 新格式：{区域代码(6位)}{业务前缀(1位)}{当日序号(3位)}，如 440300A001
            long seq = queueService.generateSequence(regionId, request.getBusinessTypeId());
            ticketNo = regionCode + bt.getPrefix() + String.format("%03d", seq);

            // 4. Insert ticket with masked phone
            ticket = new Ticket();
            ticket.setRegionId(regionId);
            ticket.setTicketNo(ticketNo);
            ticket.setBusinessTypeId(request.getBusinessTypeId());
            ticket.setSource(TicketSource.ONLINE.getValue());
            ticket.setPhone(maskedPhone);
            ticket.setName(request.getName());
            ticket.setStatus(TicketStatus.WAITING.getValue());
            ticket.setCreatedAt(now);
            ticketMapper.insert(ticket);

            // 设置 Redis 过期 Key，当日午夜自动触发过号标记
            String expireKey = "ticket:expire:" + ticket.getId();
            long secondsUntilMidnight = Duration.between(now, endOfDay).getSeconds();
            if (secondsUntilMidnight > 0) {
                stringRedisTemplate.opsForValue().set(expireKey, ticket.getTicketNo(), Duration.ofSeconds(secondsUntilMidnight));
            }

            // 5. Enqueue in Redis
            queueService.enqueue(regionId, request.getBusinessTypeId(), ticket.getId());
            queueService.incrementWaitingCount(regionId, request.getBusinessTypeId());
        } finally {
            queueService.releaseLock(lockKey);
        }

        // 6. Return response
        long waitingCount = queueService.getWaitingCount(regionId, request.getBusinessTypeId());
        int estimatedMinutes = (int)(waitingCount * 3);

        TakeTicketResponse resp = new TakeTicketResponse();
        resp.setTicketNo(ticketNo);
        resp.setBusinessType(bt.getName());
        resp.setWaitingCount((int) waitingCount);
        resp.setEstimatedWaitMinutes(estimatedMinutes);
        resp.setCreatedAt(ticket.getCreatedAt());
        return resp;
    }

    @Override
    public QueueStatusResponse getQueueStatus(String ticketNo) {
        Ticket ticket = ticketMapper.selectOne(
            new QueryWrapper<Ticket>().eq("ticket_no", ticketNo).last("LIMIT 1")
        );
        if (ticket == null) {
            throw new BusinessException(ResultCode.TICKET_NOT_FOUND);
        }

        BusinessType bt = businessTypeMapper.selectById(ticket.getBusinessTypeId());

        QueueStatusResponse resp = new QueueStatusResponse();
        resp.setTicketNo(ticketNo);
        resp.setStatus(ticket.getStatus());
        resp.setBusinessType(bt != null ? bt.getName() : "");

        if (TicketStatus.WAITING.getValue().equals(ticket.getStatus())) {
            long waitingCount = queueService.getWaitingCount(ticket.getRegionId(), ticket.getBusinessTypeId());
            resp.setWaitingCount((int) waitingCount);
            resp.setEstimatedWaitMinutes((int)(waitingCount * 3));
        }

        if (ticket.getCounterId() != null) {
            Counter counter = counterMapper.selectById(ticket.getCounterId());
            resp.setCounterName(counter != null ? counter.getName() : null);
        }

        return resp;
    }

    @Override
    @Transactional
    public void cancelTicket(CancelTicketRequest request) {
        Ticket ticket = ticketMapper.selectOne(
            new QueryWrapper<Ticket>().eq("ticket_no", request.getTicketNo())
        );
        if (ticket == null) {
            throw new BusinessException(ResultCode.TICKET_NOT_FOUND);
        }
        if (!TicketStatus.WAITING.getValue().equals(ticket.getStatus())) {
            throw new BusinessException(ResultCode.INVALID_STATE_TRANSITION);
        }

        ticket.setStatus(TicketStatus.CANCELLED.getValue());
        ticketMapper.updateById(ticket);

        queueService.dequeue(ticket.getRegionId(), ticket.getBusinessTypeId(), ticket.getId());
        queueService.decrementWaitingCount(ticket.getRegionId(), ticket.getBusinessTypeId());
    }

    @Override
    public List<MyTicketVO> getMyTickets(String phone) {
        String maskedPhone = PhoneUtil.mask(phone);
        List<Ticket> tickets = ticketMapper.selectList(
            new QueryWrapper<Ticket>()
                .eq("phone", maskedPhone)
                .orderByDesc("created_at")
                .last("LIMIT 50")
        );
        List<Long> btIds = tickets.stream()
            .map(Ticket::getBusinessTypeId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        Map<Long, BusinessType> btMap = btIds.isEmpty()
            ? Collections.emptyMap()
            : businessTypeMapper.selectBatchIds(btIds).stream()
                .filter(bt -> bt.getId() != null)
                .collect(Collectors.toMap(BusinessType::getId, v -> v, (a, b) -> a));

        List<Long> counterIds = tickets.stream()
            .map(Ticket::getCounterId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        Map<Long, Counter> counterMap = counterIds.isEmpty()
            ? Collections.emptyMap()
            : counterMapper.selectBatchIds(counterIds).stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(Counter::getId, v -> v, (a, b) -> a));

        return tickets.stream().map(t -> {
            BusinessType bt = btMap.get(t.getBusinessTypeId());
            Counter counter = t.getCounterId() != null ? counterMap.get(t.getCounterId()) : null;
            MyTicketVO vo = new MyTicketVO();
            vo.setTicketNo(t.getTicketNo());
            vo.setStatus(t.getStatus());
            vo.setStatusText(TicketStatus.fromValue(t.getStatus()).name());
            vo.setBusinessType(bt != null ? bt.getName() : "");
            vo.setCounterName(counter != null ? counter.getName() : null);
            vo.setCreatedAt(t.getCreatedAt());
            vo.setCalledAt(t.getCalledAt());
            vo.setCompletedAt(t.getCompletedAt());
            return vo;
        }).toList();
    }

    @Override
    public ActiveTicketResponse getActiveTicket(Long regionId, String phone) {
        ActiveTicketResponse resp = new ActiveTicketResponse();
        String maskedPhone = PhoneUtil.mask(phone);

        // Query unfinished tickets in this region
        List<Ticket> activeTickets = ticketMapper.selectList(
            new QueryWrapper<Ticket>()
                .eq("region_id", regionId)
                .eq("phone", maskedPhone)
                .in("status", TicketStatus.WAITING.getValue(), TicketStatus.CALLED.getValue(), TicketStatus.SERVING.getValue())
                .orderByAsc("created_at")
                .last("LIMIT 1")
        );

        // Get region info
        Region region = regionMapper.selectById(regionId);
        resp.setRegionId(region != null ? region.getId() : null);
        resp.setRegionName(region != null ? region.getRegionName() : "");

        if (activeTickets.isEmpty()) {
            resp.setHasActive(false);
            return resp;
        }

        Ticket ticket = activeTickets.get(0);
        resp.setHasActive(true);
        resp.setTicketNo(ticket.getTicketNo());
        resp.setStatus(ticket.getStatus());
        resp.setStatusText(TicketStatus.fromValue(ticket.getStatus()).getLabel());
        resp.setCreatedAt(ticket.getCreatedAt() != null ? ticket.getCreatedAt().toString() : null);

        BusinessType bt = businessTypeMapper.selectById(ticket.getBusinessTypeId());
        resp.setBusinessTypeName(bt != null ? bt.getName() : "");

        if (ticket.getCounterId() != null) {
            Counter counter = counterMapper.selectById(ticket.getCounterId());
            resp.setCounterName(counter != null ? counter.getName() : null);
        }

        if (TicketStatus.WAITING.getValue().equals(ticket.getStatus())) {
            long waitingCount = queueService.getWaitingCount(ticket.getRegionId(), ticket.getBusinessTypeId());
            resp.setWaitingCount((int) waitingCount);
            resp.setEstimatedWaitMinutes((int) (waitingCount * 3));
        } else {
            resp.setWaitingCount(0);
            resp.setEstimatedWaitMinutes(0);
        }

        return resp;
    }

    @Override
    public List<AdminTicketVO> listTickets(String status, String date, String startDate, String endDate, String phone, String name, String ticketNo) {
        return listTickets(status, date, startDate, endDate, phone, name, ticketNo, null);
    }

    public List<AdminTicketVO> listTickets(String status, String date, String startDate, String endDate, String phone, String name, String ticketNo, Set<Long> allowedRegionIds) {
        QueryWrapper<Ticket> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at").last("LIMIT 200");
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        try {
            if (date != null && !date.isEmpty()) {
                LocalDate d = LocalDate.parse(date);
                wrapper.ge("created_at", d.atStartOfDay())
                       .lt("created_at", d.plusDays(1).atStartOfDay());
            } else if ((startDate == null || startDate.isEmpty()) && (endDate == null || endDate.isEmpty())) {
                LocalDate d = LocalDate.now();
                wrapper.ge("created_at", d.atStartOfDay())
                       .lt("created_at", d.plusDays(1).atStartOfDay());
            }
            if (startDate != null && !startDate.isEmpty()) {
                LocalDate d = LocalDate.parse(startDate);
                wrapper.ge("created_at", d.atStartOfDay());
            }
            if (endDate != null && !endDate.isEmpty()) {
                LocalDate d = LocalDate.parse(endDate);
                wrapper.lt("created_at", d.plusDays(1).atStartOfDay());
            }
        } catch (DateTimeParseException ignored) {
        }
        if (phone != null && !phone.isEmpty()) {
            wrapper.like("phone", phone);
        }
        if (name != null && !name.isEmpty()) {
            wrapper.like("name", name);
        }
        if (ticketNo != null && !ticketNo.isEmpty()) {
            wrapper.like("ticket_no", ticketNo);
        }
        List<Ticket> tickets = ticketMapper.selectList(wrapper);
        // 区域权限过滤
        if (allowedRegionIds != null && allowedRegionIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (allowedRegionIds != null) {
            Set<Long> filter = allowedRegionIds;
            tickets = tickets.stream().filter(t -> t.getRegionId() != null && filter.contains(t.getRegionId())).collect(Collectors.toList());
        }

        List<Long> btIds = tickets.stream()
            .map(Ticket::getBusinessTypeId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        Map<Long, BusinessType> btMap = btIds.isEmpty()
            ? Collections.emptyMap()
            : businessTypeMapper.selectBatchIds(btIds).stream()
                .filter(bt -> bt.getId() != null)
                .collect(Collectors.toMap(BusinessType::getId, v -> v, (a, b) -> a));

        List<Long> counterIds = tickets.stream()
            .map(Ticket::getCounterId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        Map<Long, Counter> counterMap = counterIds.isEmpty()
            ? Collections.emptyMap()
            : counterMapper.selectBatchIds(counterIds).stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(Counter::getId, v -> v, (a, b) -> a));

        List<Long> regionIds = tickets.stream()
            .map(Ticket::getRegionId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        Map<Long, Region> regionMap = regionIds.isEmpty()
            ? Collections.emptyMap()
            : regionMapper.selectBatchIds(regionIds).stream()
                .filter(r -> r.getId() != null)
                .collect(Collectors.toMap(Region::getId, v -> v, (a, b) -> a));

        return tickets.stream().map(t -> {
            BusinessType bt = btMap.get(t.getBusinessTypeId());
            Counter counter = t.getCounterId() != null ? counterMap.get(t.getCounterId()) : null;
            Region region = t.getRegionId() != null ? regionMap.get(t.getRegionId()) : null;
            String regionName = region != null ? region.getRegionName() : null;
            AdminTicketVO vo = new AdminTicketVO();
            vo.setId(t.getId());
            vo.setTicketNo(t.getTicketNo());
            vo.setBusinessType(bt != null ? bt.getName() : "");
            vo.setStatus(t.getStatus());
            vo.setStatusText(TicketStatus.fromValue(t.getStatus()).name());
            vo.setPhone(t.getPhone());
            vo.setName(t.getName());
            vo.setRegionName(regionName);
            vo.setCounterName(counter != null ? counter.getName() : null);
            vo.setCreatedAt(t.getCreatedAt());
            vo.setCalledAt(t.getCalledAt());
            vo.setServedAt(t.getServedAt());
            vo.setCompletedAt(t.getCompletedAt());
            vo.setSkipType(t.getSkipType());
            vo.setReactivated(t.getReactivatedAt() != null);
            return vo;
        }).toList();
    }

    @Override
    @Transactional
    public void markAsSkipped(Long ticketId) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) return;
        String status = ticket.getStatus();
        if (TicketStatus.WAITING.getValue().equals(status)
            || TicketStatus.CALLED.getValue().equals(status)
            || TicketStatus.SERVING.getValue().equals(status)) {
            ticket.setStatus(TicketStatus.SKIPPED.getValue());
            ticket.setSkipType(com.queue.enums.SkipType.SYSTEM.getValue()); // 系统过号
            ticketMapper.updateById(ticket);
            // 仅等待中的票需要从队列移除并扣减等待人数，已叫号/服务中的票已在 callNext 时移除
            if (TicketStatus.WAITING.getValue().equals(status)) {
                queueService.dequeue(ticket.getRegionId(), ticket.getBusinessTypeId(), ticketId);
                queueService.decrementWaitingCount(ticket.getRegionId(), ticket.getBusinessTypeId());
            }
        }
    }

    @Override
    @Transactional
    public int markExpiredTickets() {
        // 查询所有非当日且仍处于未办结状态的票
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        List<Ticket> expiredTickets = ticketMapper.selectList(
            new QueryWrapper<Ticket>()
                .in("status", TicketStatus.WAITING.getValue(), TicketStatus.CALLED.getValue(), TicketStatus.SERVING.getValue())
                .lt("created_at", startOfToday)
        );
        for (Ticket ticket : expiredTickets) {
            boolean wasWaiting = TicketStatus.WAITING.getValue().equals(ticket.getStatus());
            ticket.setStatus(TicketStatus.SKIPPED.getValue());
            ticket.setSkipType(com.queue.enums.SkipType.SYSTEM.getValue()); // 系统过号
            ticketMapper.updateById(ticket);
            // 仅等待中的票需要从队列移除并扣减等待人数，已叫号/服务中的票已在 callNext 时移除
            if (wasWaiting) {
                queueService.dequeue(ticket.getRegionId(), ticket.getBusinessTypeId(), ticket.getId());
                queueService.decrementWaitingCount(ticket.getRegionId(), ticket.getBusinessTypeId());
            }
        }
        return expiredTickets.size();
    }
}
