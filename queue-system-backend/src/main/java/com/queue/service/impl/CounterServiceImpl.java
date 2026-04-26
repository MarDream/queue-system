package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.queue.common.BusinessException;
import com.queue.common.ResultCode;
import com.queue.enums.CounterStatus;
import com.queue.enums.SkipType;
import com.queue.enums.TicketStatus;
import com.queue.entity.Counter;
import com.queue.entity.Ticket;
import com.queue.entity.BusinessType;
import com.queue.dto.CounterCallResponse;
import com.queue.mapper.BusinessTypeMapper;
import com.queue.mapper.CounterBusinessMapper;
import com.queue.mapper.CounterMapper;
import com.queue.mapper.RegionMapper;
import com.queue.mapper.TicketMapper;
import com.queue.service.CounterService;
import com.queue.service.QueueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CounterServiceImpl implements CounterService {

    private final CounterMapper counterMapper;
    private final CounterBusinessMapper counterBusinessMapper;
    private final TicketMapper ticketMapper;
    private final QueueService queueService;
    private final BusinessTypeMapper businessTypeMapper;
    private final RegionMapper regionMapper;

    public CounterServiceImpl(CounterMapper counterMapper,
                              CounterBusinessMapper counterBusinessMapper,
                              TicketMapper ticketMapper,
                              QueueService queueService,
                              BusinessTypeMapper businessTypeMapper,
                              RegionMapper regionMapper) {
        this.counterMapper = counterMapper;
        this.counterBusinessMapper = counterBusinessMapper;
        this.ticketMapper = ticketMapper;
        this.queueService = queueService;
        this.businessTypeMapper = businessTypeMapper;
        this.regionMapper = regionMapper;
    }

    @Override
    public CounterCallResponse callNext(Long counterId) {
        String lockKey = "call:" + counterId;
        if (!queueService.acquireLock(lockKey, 5)) {
            throw new BusinessException(ResultCode.COUNTER_NOT_OPERABLE);
        }
        try {
            Counter counter = counterMapper.selectById(counterId);
            if (counter == null) throw new BusinessException(ResultCode.COUNTER_NOT_OPERABLE);
            if (CounterStatus.PAUSED.getValue().equals(counter.getStatus())) {
                throw new BusinessException(ResultCode.COUNTER_NOT_OPERABLE);
            }

            List<Long> businessTypeIds = counterBusinessMapper.selectBusinessTypeIdsByCounterId(counterId);
            if (businessTypeIds == null || businessTypeIds.isEmpty()) {
                return null;
            }

            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1);

            // 从数据库查询当前窗口支持业务类型中最早的等待票
            QueryWrapper<Ticket> wrapper = new QueryWrapper<>();
            wrapper.in("business_type_id", businessTypeIds)
                   .eq("region_id", counter.getRegionId())
                   .eq("status", TicketStatus.WAITING.getValue())
                   .ge("created_at", startOfDay)
                   .lt("created_at", endOfDay)
                   .orderByAsc("created_at")
                   .last("LIMIT 1");
            Ticket bestTicket = ticketMapper.selectOne(wrapper);

            if (bestTicket == null) {
                return null;
            }

            // 使用票自身的regionId进行Redis操作
            queueService.dequeue(bestTicket.getRegionId(), bestTicket.getBusinessTypeId(), bestTicket.getId());
            queueService.decrementWaitingCount(bestTicket.getRegionId(), bestTicket.getBusinessTypeId());

            bestTicket.setStatus(TicketStatus.CALLED.getValue());
            bestTicket.setCounterId(counterId);
            bestTicket.setCalledAt(LocalDateTime.now());
            ticketMapper.updateById(bestTicket);

            counter.setStatus(CounterStatus.BUSY.getValue());
            counter.setCurrentTicketId(bestTicket.getId());
            counterMapper.updateById(counter);

            BusinessType bt = businessTypeMapper.selectById(bestTicket.getBusinessTypeId());
            CounterCallResponse resp = new CounterCallResponse();
            resp.setTicketNo(bestTicket.getTicketNo());
            resp.setBusinessType(bt != null ? bt.getName() : "");
            resp.setCustomerName(bestTicket.getName());
            // 拼接区域完整路径 + 窗口编号，如：深圳市-南山区1号窗口
            String regionPath = regionMapper.selectFullRegionPath(counter.getRegionId());
            resp.setCounterName(regionPath != null ? regionPath + counter.getNumber() + "号窗口" : counter.getName());
            resp.setCalledAt(bestTicket.getCalledAt());
            return resp;
        } finally {
            queueService.releaseLock(lockKey);
        }
    }

    @Override
    public CounterCallResponse recall(Long counterId) {
        Counter counter = counterMapper.selectById(counterId);
        if (counter == null || counter.getCurrentTicketId() == null) {
            throw new BusinessException(ResultCode.COUNTER_NOT_OPERABLE);
        }
        Ticket ticket = ticketMapper.selectById(counter.getCurrentTicketId());
        if (ticket == null) throw new BusinessException(ResultCode.TICKET_NOT_FOUND);

        ticket.setStatus(TicketStatus.CALLED.getValue());
        ticket.setCalledAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);

        BusinessType bt = businessTypeMapper.selectById(ticket.getBusinessTypeId());
        CounterCallResponse resp = new CounterCallResponse();
        resp.setTicketNo(ticket.getTicketNo());
        resp.setBusinessType(bt != null ? bt.getName() : "");
        resp.setCustomerName(ticket.getName());
        // 拼接区域完整路径 + 窗口编号，如：深圳市-南山区1号窗口
        String regionPath = regionMapper.selectFullRegionPath(counter.getRegionId());
        resp.setCounterName(regionPath != null ? regionPath + counter.getNumber() + "号窗口" : counter.getName());
        resp.setCalledAt(ticket.getCalledAt());
        return resp;
    }

    @Override
    public void skip(Long counterId) {
        String lockKey = "call:" + counterId;
        if (!queueService.acquireLock(lockKey, 5)) {
            throw new BusinessException(ResultCode.COUNTER_NOT_OPERABLE);
        }
        try {
            Counter counter = counterMapper.selectById(counterId);
            if (counter == null || counter.getCurrentTicketId() == null) {
                throw new BusinessException(ResultCode.COUNTER_NOT_OPERABLE);
            }
            if (!CounterStatus.BUSY.getValue().equals(counter.getStatus())) {
                throw new BusinessException(ResultCode.COUNTER_NOT_OPERABLE);
            }
            Ticket ticket = ticketMapper.selectById(counter.getCurrentTicketId());
            if (ticket == null) throw new BusinessException(ResultCode.TICKET_NOT_FOUND);
            // 仅允许已叫号或服务中状态可跳过
            String status = ticket.getStatus();
            if (!TicketStatus.CALLED.getValue().equals(status) && !TicketStatus.SERVING.getValue().equals(status)) {
                throw new BusinessException(ResultCode.INVALID_STATE_TRANSITION);
            }

            ticket.setStatus(TicketStatus.SKIPPED.getValue());
            ticket.setSkipType(SkipType.MANUAL.getValue()); // 人工跳过
            ticketMapper.updateById(ticket);

            counter.setStatus(CounterStatus.IDLE.getValue());
            counter.setCurrentTicketId(null);
            counterMapper.updateById(counter);
        } finally {
            queueService.releaseLock(lockKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void serve(Long counterId) {
        String lockKey = "call:" + counterId;
        if (!queueService.acquireLock(lockKey, 5)) {
            throw new BusinessException(ResultCode.COUNTER_NOT_OPERABLE);
        }
        try {
            Counter counter = counterMapper.selectById(counterId);
            if (counter == null || counter.getCurrentTicketId() == null) {
                throw new BusinessException(ResultCode.COUNTER_NOT_OPERABLE);
            }
            Ticket ticket = ticketMapper.selectById(counter.getCurrentTicketId());
            if (ticket == null) throw new BusinessException(ResultCode.TICKET_NOT_FOUND);

            String status = ticket.getStatus();
            if (!TicketStatus.CALLED.getValue().equals(status) && !TicketStatus.SERVING.getValue().equals(status)) {
                throw new BusinessException(ResultCode.INVALID_STATE_TRANSITION);
            }

            ticket.setStatus(TicketStatus.SERVING.getValue());
            ticket.setServedAt(LocalDateTime.now());
            ticketMapper.updateById(ticket);
        } finally {
            queueService.releaseLock(lockKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long counterId) {
        String lockKey = "call:" + counterId;
        if (!queueService.acquireLock(lockKey, 5)) {
            throw new BusinessException(ResultCode.COUNTER_NOT_OPERABLE);
        }
        try {
            Counter counter = counterMapper.selectById(counterId);
            if (counter == null || counter.getCurrentTicketId() == null) {
                throw new BusinessException(ResultCode.COUNTER_NOT_OPERABLE);
            }
            if (!CounterStatus.BUSY.getValue().equals(counter.getStatus())) {
                throw new BusinessException(ResultCode.COUNTER_NOT_OPERABLE);
            }
            Ticket ticket = ticketMapper.selectById(counter.getCurrentTicketId());
            if (ticket == null) throw new BusinessException(ResultCode.TICKET_NOT_FOUND);

            // 仅允许已叫号或服务中状态可完成
            String status = ticket.getStatus();
            if (!TicketStatus.CALLED.getValue().equals(status) && !TicketStatus.SERVING.getValue().equals(status)) {
                throw new BusinessException(ResultCode.INVALID_STATE_TRANSITION);
            }

            ticket.setStatus(TicketStatus.COMPLETED.getValue());
            ticket.setCompletedAt(LocalDateTime.now());
            ticketMapper.updateById(ticket);

            counter.setStatus(CounterStatus.IDLE.getValue());
            counter.setCurrentTicketId(null);
            counterMapper.updateById(counter);
        } finally {
            queueService.releaseLock(lockKey);
        }
    }

    @Override
    public void togglePause(Long counterId) {
        String lockKey = "call:" + counterId;
        if (!queueService.acquireLock(lockKey, 5)) {
            throw new BusinessException(ResultCode.COUNTER_NOT_OPERABLE);
        }
        try {
            Counter counter = counterMapper.selectById(counterId);
            if (counter == null) throw new BusinessException(ResultCode.COUNTER_NOT_OPERABLE);
            if (CounterStatus.BUSY.getValue().equals(counter.getStatus())) {
                throw new BusinessException(ResultCode.COUNTER_NOT_OPERABLE);
            }
            if (CounterStatus.PAUSED.getValue().equals(counter.getStatus())) {
                counter.setStatus(CounterStatus.IDLE.getValue());
            } else {
                counter.setStatus(CounterStatus.PAUSED.getValue());
            }
            counterMapper.updateById(counter);
        } finally {
            queueService.releaseLock(lockKey);
        }
    }

    @Override
    @Transactional
    public void reactivateSkippedTicket(String ticketNo) {
        if (ticketNo == null || ticketNo.isBlank()) {
            throw new BusinessException(ResultCode.TICKET_NOT_FOUND);
        }
        Ticket ticket = ticketMapper.selectOne(
            new QueryWrapper<Ticket>()
                .eq("ticket_no", ticketNo)
                .last("LIMIT 1")
        );
        if (ticket == null) {
            throw new BusinessException(ResultCode.TICKET_NOT_FOUND);
        }
        if (!TicketStatus.SKIPPED.getValue().equals(ticket.getStatus())) {
            throw new BusinessException(50001, "只有已过号状态的票才能重新激活");
        }
        // 检查是否为当日票
        LocalDateTime today = LocalDateTime.now();
        LocalDateTime startOfDay = today.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = today.toLocalDate().plusDays(1).atStartOfDay();
        if (ticket.getCreatedAt() == null ||
            ticket.getCreatedAt().isBefore(startOfDay) || ticket.getCreatedAt().isAfter(endOfDay)) {
            throw new BusinessException(50001, "只能重新激活当日过号的票");
        }

        // 重新激活：状态改为等待，清空过号类型，counterId 置空
        ticket.setStatus(TicketStatus.WAITING.getValue());
        ticket.setSkipType(null);
        ticket.setCounterId(null);
        ticketMapper.updateById(ticket);

        // 重新加入等待队列（队首）
        queueService.enqueueAtFront(ticket.getRegionId(), ticket.getBusinessTypeId(), ticket.getId());
        queueService.incrementWaitingCount(ticket.getRegionId(), ticket.getBusinessTypeId());
    }
}
