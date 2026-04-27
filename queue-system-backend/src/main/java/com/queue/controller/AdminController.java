package com.queue.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.queue.common.BusinessException;
import com.queue.common.Result;
import com.queue.common.ResultCode;
import com.queue.dto.AdminTicketVO;
import com.queue.dto.BusinessTypeDetailVO;
import com.queue.dto.CounterDTO;
import com.queue.dto.CounterStatsVO;
import com.queue.entity.BusinessType;
import com.queue.entity.Counter;
import com.queue.entity.CounterBusiness;
import com.queue.entity.CounterOperator;
import com.queue.entity.Region;
import com.queue.entity.SysUser;
import com.queue.entity.Ticket;
import com.queue.enums.CounterStatus;
import com.queue.enums.TicketStatus;
import com.queue.mapper.BusinessTypeMapper;
import com.queue.mapper.CounterBusinessMapper;
import com.queue.mapper.CounterMapper;
import com.queue.mapper.CounterOperatorMapper;
import com.queue.mapper.SysUserMapper;
import com.queue.mapper.TicketMapper;
import com.queue.service.BusinessTypeService;
import com.queue.service.QueueService;
import com.queue.service.RegionBusinessService;
import com.queue.service.RegionService;
import com.queue.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final BusinessTypeService businessTypeService;
    private final RegionBusinessService regionBusinessService;
    private final TicketService ticketService;
    private final RegionService regionService;
    private final BusinessTypeMapper businessTypeMapper;
    private final CounterMapper counterMapper;
    private final CounterBusinessMapper counterBusinessMapper;
    private final CounterOperatorMapper counterOperatorMapper;
    private final TicketMapper ticketMapper;
    private final SysUserMapper sysUserMapper;
    private final QueueService queueService;

    public AdminController(BusinessTypeService businessTypeService,
                          RegionBusinessService regionBusinessService,
                          TicketService ticketService,
                          RegionService regionService,
                          BusinessTypeMapper businessTypeMapper,
                          CounterMapper counterMapper,
                          CounterBusinessMapper counterBusinessMapper,
                          CounterOperatorMapper counterOperatorMapper,
                          TicketMapper ticketMapper,
                          SysUserMapper sysUserMapper,
                          QueueService queueService) {
        this.businessTypeService = businessTypeService;
        this.regionBusinessService = regionBusinessService;
        this.ticketService = ticketService;
        this.regionService = regionService;
        this.businessTypeMapper = businessTypeMapper;
        this.counterMapper = counterMapper;
        this.counterBusinessMapper = counterBusinessMapper;
        this.counterOperatorMapper = counterOperatorMapper;
        this.ticketMapper = ticketMapper;
        this.sysUserMapper = sysUserMapper;
        this.queueService = queueService;
    }

    // Business Types CRUD
    @GetMapping("/business-types")
    public Result<List<BusinessType>> listBusinessTypes(@RequestParam(required = false) Long regionId) {
        if (regionId != null) {
            // 返回该区域关联的业务类型（用于窗口管理等场景）
            return Result.ok(regionBusinessService.listBusinessTypesByRegion(regionId));
        }
        return Result.ok(businessTypeService.listAll());
    }

    @GetMapping("/business-types/{id}")
    public Result<BusinessType> getBusinessType(@PathVariable Long id) {
        return Result.ok(businessTypeService.getById(id));
    }

    @PostMapping("/business-types")
    public Result<BusinessType> createBusinessType(@Valid @RequestBody BusinessType businessType) {
        return Result.ok(businessTypeService.create(businessType));
    }

    @PutMapping("/business-types/{id}")
    public Result<BusinessType> updateBusinessType(@PathVariable Long id, @RequestBody BusinessType businessType) {
        businessType.setId(id);
        return Result.ok(businessTypeService.update(businessType));
    }

    @DeleteMapping("/business-types/{id}")
    public Result<Void> deleteBusinessType(@PathVariable Long id) {
        businessTypeService.delete(id);
        return Result.ok();
    }

    // Counters CRUD
    @GetMapping("/counters")
    public Result<List<CounterDTO>> listCounters(@RequestParam(required = false) Long regionId,
                                                  @RequestParam(required = false) Long userId) {
        QueryWrapper<Counter> qw = new QueryWrapper<>();

        // 区域权限过滤
        Set<Long> allowedRegionIds = null;
        if (userId != null && userId > 0) {
            SysUser user = sysUserMapper.selectById(userId);
            if (user != null && !"SUPER_ADMIN".equals(user.getRole())) {
                List<Long> scopedRoots = sysUserMapper.selectRegionScopeIds(userId);
                if (scopedRoots != null && !scopedRoots.isEmpty()) {
                    Set<Long> all = new HashSet<>();
                    for (Long rid : scopedRoots) {
                        if (rid == null) continue;
                        all.addAll(regionService.getDescendantRegionIds(rid));
                    }
                    allowedRegionIds = all;
                } else if (user.getRegionCode() != null && !user.getRegionCode().isEmpty()) {
                    Region userRegion = regionService.getByCode(user.getRegionCode());
                    if (userRegion != null) {
                        List<Long> descendants = regionService.getDescendantRegionIds(userRegion.getId());
                        allowedRegionIds = descendants.stream().collect(Collectors.toSet());
                    } else {
                        allowedRegionIds = Collections.emptySet();
                    }
                } else {
                    allowedRegionIds = Collections.emptySet();
                }
            }
            // SUPER_ADMIN: allowedRegionIds = null，不做过滤
        } else if (regionId != null) {
            // 保留旧的兼容：直接按 regionId 过滤
            qw.eq("region_id", regionId);
        }

        if (allowedRegionIds != null && !allowedRegionIds.isEmpty()) {
            qw.in("region_id", allowedRegionIds);
        } else if (allowedRegionIds != null && allowedRegionIds.isEmpty()) {
            // 有权限限制但没有可用区域 → 返回空列表
            return Result.ok(Collections.emptyList());
        }

        // 窗口操作员只能看到自己被分配的窗口
        if (userId != null && userId > 0) {
            SysUser user = sysUserMapper.selectById(userId);
            if (user != null && "WINDOW_OPERATOR".equals(user.getRole())) {
                List<Long> assignedCounterIds = counterOperatorMapper.selectCounterIdsByUserId(userId);
                if (assignedCounterIds == null || assignedCounterIds.isEmpty()) {
                    return Result.ok(Collections.emptyList());
                }
                qw.in("id", assignedCounterIds);
            }
        }

        List<Counter> counters = counterMapper.selectList(qw);
        List<CounterDTO> dtos = counters.stream().map(c -> {
            // 状态恢复：柜台为 busy 但无有效服务中票号，自动恢复为 idle
            if (CounterStatus.BUSY.getValue().equals(c.getStatus())) {
                boolean needReset = true;
                if (c.getCurrentTicketId() != null) {
                    Ticket ticket = ticketMapper.selectById(c.getCurrentTicketId());
                    if (ticket != null && (TicketStatus.CALLED.getValue().equals(ticket.getStatus()) || TicketStatus.SERVING.getValue().equals(ticket.getStatus()))) {
                        needReset = false;
                    }
                }
                if (needReset) {
                    c.setStatus(CounterStatus.IDLE.getValue());
                    c.setCurrentTicketId(null);
                    counterMapper.updateById(c);
                }
            }
            CounterDTO dto = new CounterDTO();
            dto.setId(c.getId());
            dto.setRegionId(c.getRegionId());
            dto.setNumber(c.getNumber());
            dto.setName(c.getName());
            dto.setStatus(c.getStatus());
            dto.setOperatorName(c.getOperatorName());
            List<Long> btIds = counterBusinessMapper.selectBusinessTypeIdsByCounterId(c.getId());
            dto.setBusinessTypeIds(btIds);
            List<BusinessType> businessTypes = btIds.stream()
                .map(businessTypeMapper::selectById)
                .filter(bt -> bt != null)
                .collect(Collectors.toList());
            dto.setBusinessTypes(businessTypes);
            // 操作员信息
            List<Long> operatorIds = counterOperatorMapper.selectUserIdsByCounterId(c.getId());
            dto.setOperatorIds(operatorIds);
            List<SysUser> operators = counterOperatorMapper.selectOperatorsByCounterId(c.getId());
            dto.setOperatorNames(operators.stream().map(SysUser::getName).collect(Collectors.toList()));
            return dto;
        }).collect(Collectors.toList());
        return Result.ok(dtos);
    }

    @GetMapping("/counters/{id}")
    public Result<CounterDTO> getCounter(@PathVariable Long id) {
        Counter c = counterMapper.selectById(id);
        if (c == null) return Result.error(400, "窗口不存在");
        CounterDTO dto = new CounterDTO();
        dto.setId(c.getId());
        dto.setRegionId(c.getRegionId());
        dto.setNumber(c.getNumber());
        dto.setName(c.getName());
        dto.setStatus(c.getStatus());
        dto.setOperatorName(c.getOperatorName());
        dto.setBusinessTypeIds(counterBusinessMapper.selectBusinessTypeIdsByCounterId(id));
        List<BusinessType> businessTypes = dto.getBusinessTypeIds().stream()
            .map(businessTypeMapper::selectById)
            .filter(bt -> bt != null)
            .collect(Collectors.toList());
        dto.setBusinessTypes(businessTypes);
        // 操作员信息
        List<Long> operatorIds = counterOperatorMapper.selectUserIdsByCounterId(id);
        dto.setOperatorIds(operatorIds);
        List<SysUser> operators = counterOperatorMapper.selectOperatorsByCounterId(id);
        dto.setOperatorNames(operators.stream().map(SysUser::getName).collect(Collectors.toList()));
        return Result.ok(dto);
    }

    @PostMapping("/counters")
    @Transactional
    public Result<CounterDTO> createCounter(@RequestBody CounterDTO dto,
            @RequestParam(required = false) Long userId) {
        // 新增：校验用户是否有权在该区域创建
        checkRegionAccess(userId, dto.getRegionId());
        Counter counter = new Counter();
        counter.setRegionId(dto.getRegionId());
        counter.setNumber(dto.getNumber());
        counter.setName(dto.getName());
        counter.setStatus(CounterStatus.IDLE.getValue());
        counter.setOperatorName(dto.getOperatorName());
        counterMapper.insert(counter);

        // Insert business type associations
        if (dto.getBusinessTypeIds() != null) {
            for (Long btId : dto.getBusinessTypeIds()) {
                if (businessTypeMapper.selectById(btId) == null) {
                    throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE);
                }
                CounterBusiness cb = new CounterBusiness();
                cb.setCounterId(counter.getId());
                cb.setBusinessTypeId(btId);
                counterBusinessMapper.insert(cb);
            }
        }

        // Insert operator associations
        if (dto.getOperatorIds() != null) {
            for (Long opUserId : dto.getOperatorIds()) {
                SysUser user = sysUserMapper.selectById(opUserId);
                if (user == null || !"WINDOW_OPERATOR".equals(user.getRole())) {
                    throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "无效的操作员: " + opUserId);
                }
                CounterOperator co = new CounterOperator();
                co.setCounterId(counter.getId());
                co.setUserId(opUserId);
                counterOperatorMapper.insert(co);
            }
        }

        dto.setId(counter.getId());
        dto.setStatus(CounterStatus.IDLE.getValue());
        return Result.ok(dto);
    }

    @PutMapping("/counters/{id}")
    @Transactional
    public Result<Void> updateCounter(@PathVariable Long id, @RequestBody CounterDTO dto,
            @RequestParam(required = false) Long userId) {
        Counter counter = counterMapper.selectById(id);
        if (counter == null) return Result.error(400, "窗口不存在");
        // 检查原区域权限
        checkRegionAccess(userId, counter.getRegionId());

        counter.setRegionId(dto.getRegionId());
        counter.setNumber(dto.getNumber());
        counter.setName(dto.getName());
        counter.setOperatorName(dto.getOperatorName());
        if (dto.getStatus() != null) {
            counter.setStatus(dto.getStatus());
        }
        counterMapper.updateById(counter);

        // Delete and recreate business type associations
        counterBusinessMapper.delete(new QueryWrapper<CounterBusiness>().eq("counter_id", id));
        if (dto.getBusinessTypeIds() != null) {
            for (Long btId : dto.getBusinessTypeIds()) {
                if (businessTypeMapper.selectById(btId) == null) {
                    throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE);
                }
                CounterBusiness cb = new CounterBusiness();
                cb.setCounterId(id);
                cb.setBusinessTypeId(btId);
                counterBusinessMapper.insert(cb);
            }
        }

        // Delete and recreate operator associations
        counterOperatorMapper.deleteByCounterId(id);
        if (dto.getOperatorIds() != null) {
            for (Long opUserId : dto.getOperatorIds()) {
                SysUser user = sysUserMapper.selectById(opUserId);
                if (user == null || !"WINDOW_OPERATOR".equals(user.getRole())) {
                    throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "无效的操作员: " + opUserId);
                }
                CounterOperator co = new CounterOperator();
                co.setCounterId(id);
                co.setUserId(opUserId);
                counterOperatorMapper.insert(co);
            }
        }
        return Result.ok();
    }

    @DeleteMapping("/counters/{id}")
    public Result<Void> deleteCounter(@PathVariable Long id,
            @RequestParam(required = false) Long userId) {
        Counter counter = counterMapper.selectById(id);
        if (counter == null) return Result.error(400, "窗口不存在");
        // 检查区域权限
        checkRegionAccess(userId, counter.getRegionId());
        if (!CounterStatus.IDLE.getValue().equals(counter.getStatus())) {
            return Result.error(ResultCode.COUNTER_NOT_OPERABLE);
        }
        // 物理删除（直接删除，不做软删除）
        counterMapper.physicalDeleteById(id);
        counterBusinessMapper.delete(new QueryWrapper<CounterBusiness>().eq("counter_id", id));
        counterOperatorMapper.deleteByCounterId(id);
        return Result.ok();
    }

    // Window stats detail API
    @GetMapping("/counters/{id}/stats")
    public Result<CounterStatsVO> getCounterStats(@PathVariable Long id) {
        Counter counter = counterMapper.selectById(id);
        if (counter == null) return Result.error(400, "窗口不存在");

        CounterStatsVO stats = new CounterStatsVO();
        stats.setCurrentStatus(counter.getStatus());

        // 当前服务中的票号
        if (counter.getCurrentTicketId() != null) {
            Ticket curTicket = ticketMapper.selectById(counter.getCurrentTicketId());
            if (curTicket != null) {
                stats.setCurrentTicketNo(curTicket.getTicketNo());
                BusinessType bt = businessTypeMapper.selectById(curTicket.getBusinessTypeId());
                stats.setCurrentBusinessTypeName(bt != null ? bt.getName() : "");
            }
        }

        // 今日该窗口的票
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        List<Ticket> todayTickets = ticketMapper.selectList(
            new QueryWrapper<Ticket>()
                .eq("counter_id", id)
                .ge("created_at", startOfDay)
                .lt("created_at", endOfDay)
        );

        // 统计
        stats.setTodayServedCount((int) todayTickets.stream()
            .filter(t -> "completed".equals(t.getStatus())).count());
        stats.setTodayCalledCount((int) todayTickets.stream()
            .filter(t -> "called".equals(t.getStatus()) || "serving".equals(t.getStatus()) || "completed".equals(t.getStatus())).count());
        stats.setTodaySkippedCount((int) todayTickets.stream()
            .filter(t -> "skipped".equals(t.getStatus())).count());

        // 平均服务时长
        List<Ticket> completedWithServe = todayTickets.stream()
            .filter(t -> "completed".equals(t.getStatus()) && t.getServedAt() != null && t.getCompletedAt() != null)
            .collect(Collectors.toList());
        if (!completedWithServe.isEmpty()) {
            double avg = completedWithServe.stream()
                .mapToLong(t -> Duration.between(t.getServedAt(), t.getCompletedAt()).toMinutes())
                .average().orElse(0.0);
            stats.setAvgServiceMinutes(Math.round(avg * 10.0) / 10.0);
        }

        // 平均等待时长
        List<Ticket> completedOrSkipped = todayTickets.stream()
            .filter(t -> ("completed".equals(t.getStatus()) || "skipped".equals(t.getStatus())) && t.getCalledAt() != null && t.getCreatedAt() != null)
            .collect(Collectors.toList());
        if (!completedOrSkipped.isEmpty()) {
            double avg = completedOrSkipped.stream()
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getCalledAt()).toMinutes())
                .average().orElse(0.0);
            stats.setAvgWaitMinutes(Math.round(avg * 10.0) / 10.0);
        }

        // 支持的业务类型及其等待人数
        List<Long> btIds = counterBusinessMapper.selectBusinessTypeIdsByCounterId(id);
        List<CounterStatsVO.BusinessWaitingInfo> waitingInfos = btIds.stream()
            .map(btId -> {
                BusinessType bt = businessTypeMapper.selectById(btId);
                if (bt == null) return null;
                CounterStatsVO.BusinessWaitingInfo info = new CounterStatsVO.BusinessWaitingInfo();
                info.setBusinessTypeId(btId);
                info.setBusinessTypeName(bt.getName());
                info.setPrefix(bt.getPrefix());
                info.setWaitingCount((int) queueService.getWaitingCount(counter.getRegionId(), btId));
                return info;
            })
            .filter(info -> info != null)
            .collect(Collectors.toList());
        stats.setWaitingByBusiness(waitingInfos);

        // 最近服务记录（最近10条）
        List<Ticket> recentTickets = todayTickets.stream()
            .filter(t -> t.getCalledAt() != null || t.getCompletedAt() != null)
            .sorted((a, b) -> {
                LocalDateTime aTime = a.getCompletedAt() != null ? a.getCompletedAt() : a.getCalledAt();
                LocalDateTime bTime = b.getCompletedAt() != null ? b.getCompletedAt() : b.getCalledAt();
                if (aTime == null) return 1;
                if (bTime == null) return -1;
                return bTime.compareTo(aTime);
            })
            .limit(10)
            .collect(Collectors.toList());

        List<CounterStatsVO.RecentServiceRecord> recentRecords = recentTickets.stream().map(t -> {
            CounterStatsVO.RecentServiceRecord rec = new CounterStatsVO.RecentServiceRecord();
            rec.setTicketNo(t.getTicketNo());
            BusinessType bt = businessTypeMapper.selectById(t.getBusinessTypeId());
            rec.setBusinessTypeName(bt != null ? bt.getName() : "");
            rec.setCustomerName(t.getName() != null ? t.getName() : "");
            rec.setStatus(t.getStatus());
            rec.setCalledAt(t.getCalledAt() != null ? t.getCalledAt().toString() : "");
            rec.setCompletedAt(t.getCompletedAt() != null ? t.getCompletedAt().toString() : "");
            if (t.getServedAt() != null && t.getCompletedAt() != null) {
                rec.setServiceMinutes(Math.round(Duration.between(t.getServedAt(), t.getCompletedAt()).toMinutes() * 10.0) / 10.0);
            }
            return rec;
        }).collect(Collectors.toList());
        stats.setRecentServices(recentRecords);

        return Result.ok(stats);
    }

    // Get window operators by region
    @GetMapping("/operators")
    public Result<List<SysUser>> getOperatorsByRegion(@RequestParam Long regionId) {
        return Result.ok(sysUserMapper.selectByRegionIdAndRole(regionId, "WINDOW_OPERATOR"));
    }

    /**
     * Check if user has access to a specific region.
     */
    private void checkRegionAccess(Long userId, Long regionId) {
        if (userId == null || userId <= 0) {
            return;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || "SUPER_ADMIN".equals(user.getRole())) {
            return;
        }
        Set<Long> allowed = resolveAllowedRegionIds(user);
        if (allowed.isEmpty() || !allowed.contains(regionId)) {
            throw new BusinessException(403, "无权限操作该区域");
        }
    }

    private Set<Long> resolveAllowedRegionIds(SysUser user) {
        if (user == null || "SUPER_ADMIN".equals(user.getRole())) {
            return null;
        }
        List<Long> scopedRoots = sysUserMapper.selectRegionScopeIds(user.getId());
        if (scopedRoots != null && !scopedRoots.isEmpty()) {
            Set<Long> all = new HashSet<>();
            for (Long rid : scopedRoots) {
                if (rid == null) continue;
                all.addAll(regionService.getDescendantRegionIds(rid));
            }
            return all;
        }

        Long rootId = user.getRegionId();
        if (rootId == null && user.getRegionCode() != null && !user.getRegionCode().isEmpty()) {
            Region r = regionService.getByCode(user.getRegionCode());
            rootId = r == null ? null : r.getId();
        }
        if (rootId == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(regionService.getDescendantRegionIds(rootId));
    }

    // Ticket list for admin
    @GetMapping("/tickets")
    public Result<List<AdminTicketVO>> listTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String ticketNo,
            @RequestParam(required = false) Long userId) {
        Set<Long> allowedRegionIds = null;
        if (userId != null && userId > 0) {
            SysUser user = sysUserMapper.selectById(userId);
            if (user != null && !"SUPER_ADMIN".equals(user.getRole())) {
                Set<Long> allowed = resolveAllowedRegionIds(user);
                allowedRegionIds = allowed == null ? null : allowed;
            }
        }
        return Result.ok(ticketService.listTickets(status, date, startDate, endDate, phone, name, ticketNo, allowedRegionIds));
    }

    // Business type detail stats: region + counter + operator + ticket count
    @GetMapping("/business-types/{id}/detail")
    public Result<List<BusinessTypeDetailVO>> getBusinessTypeDetail(@PathVariable Long id,
                                                                     @RequestParam(required = false) Long userId) {
        Set<Long> allowedRegionIds = null;
        if (userId != null && userId > 0) {
            SysUser user = sysUserMapper.selectById(userId);
            if (user != null && !"SUPER_ADMIN".equals(user.getRole())) {
                Set<Long> allowed = resolveAllowedRegionIds(user);
                allowedRegionIds = allowed == null ? null : allowed;
            }
        }
        return Result.ok(businessTypeService.getBusinessTypeDetail(id, allowedRegionIds));
    }
}
