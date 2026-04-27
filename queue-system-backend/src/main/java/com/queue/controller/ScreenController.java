package com.queue.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.queue.common.Result;
import lombok.extern.slf4j.Slf4j;
import com.queue.dto.DashboardResponse;
import com.queue.dto.ScreenDataResponse;
import com.queue.entity.BusinessType;
import com.queue.entity.Counter;
import com.queue.entity.Region;
import com.queue.entity.SysUser;
import com.queue.entity.Ticket;
import com.queue.mapper.BusinessTypeMapper;
import com.queue.mapper.CounterMapper;
import com.queue.mapper.RegionMapper;
import com.queue.mapper.SysUserMapper;
import com.queue.mapper.TicketMapper;
import com.queue.service.RegionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@Slf4j
public class ScreenController {

    private final TicketMapper ticketMapper;
    private final CounterMapper counterMapper;
    private final BusinessTypeMapper businessTypeMapper;
    private final RegionMapper regionMapper;
    private final RegionService regionService;
    private final SysUserMapper sysUserMapper;

    public ScreenController(TicketMapper ticketMapper,
                           CounterMapper counterMapper,
                           BusinessTypeMapper businessTypeMapper,
                           RegionMapper regionMapper,
                           RegionService regionService,
                           SysUserMapper sysUserMapper) {
        this.ticketMapper = ticketMapper;
        this.counterMapper = counterMapper;
        this.businessTypeMapper = businessTypeMapper;
        this.regionMapper = regionMapper;
        this.regionService = regionService;
        this.sysUserMapper = sysUserMapper;
    }

    @GetMapping("/queue/screen")
    public Result<ScreenDataResponse> getScreenData(@RequestParam(required = false) String regionCode) {
        ScreenDataResponse resp = new ScreenDataResponse();

        // 按 regionCode 确定过滤范围
        Set<Long> regionFilter = null;
        if (regionCode != null && !regionCode.isEmpty()) {
            Region region = regionMapper.selectOne(new QueryWrapper<Region>().eq("region_code", regionCode));
            if (region != null) {
                List<Long> descendantIds = regionService.getDescendantRegionIds(region.getId());
                regionFilter = new HashSet<>(descendantIds);
            } else {
                regionFilter = Collections.emptySet();
            }
        }
        final Set<Long> finalRegionFilter = regionFilter;

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        final List<Ticket> finalTodayTickets;
        if (finalRegionFilter != null && finalRegionFilter.isEmpty()) {
            finalTodayTickets = Collections.emptyList();
        } else {
            QueryWrapper<Ticket> ticketQw = new QueryWrapper<Ticket>()
                .ge("created_at", startOfDay)
                .lt("created_at", endOfDay);
            if (finalRegionFilter != null) {
                ticketQw.in("region_id", finalRegionFilter);
            }
            finalTodayTickets = ticketMapper.selectList(ticketQw);
        }

        final List<Counter> counters;
        if (finalRegionFilter != null && finalRegionFilter.isEmpty()) {
            counters = Collections.emptyList();
        } else if (finalRegionFilter != null) {
            counters = counterMapper.selectList(new QueryWrapper<Counter>().in("region_id", finalRegionFilter));
        } else {
            counters = counterMapper.selectList(new QueryWrapper<Counter>());
        }

        Map<Long, Counter> counterMap = counters.stream()
            .filter(c -> c.getId() != null)
            .collect(Collectors.toMap(Counter::getId, v -> v, (a, b) -> a));

        Map<Long, Ticket> ticketMap = finalTodayTickets.stream()
            .filter(t -> t.getId() != null)
            .collect(Collectors.toMap(Ticket::getId, v -> v, (a, b) -> a));

        List<Long> btIds = finalTodayTickets.stream()
            .map(Ticket::getBusinessTypeId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        Map<Long, BusinessType> btMap = btIds.isEmpty()
            ? Collections.emptyMap()
            : businessTypeMapper.selectBatchIds(btIds).stream()
                .filter(bt -> bt.getId() != null)
                .collect(Collectors.toMap(BusinessType::getId, v -> v, (a, b) -> a));

        // currentCalls: status in CALLED or SERVING
        List<Ticket> active = finalTodayTickets.stream()
            .filter(t -> "called".equals(t.getStatus()) || "serving".equals(t.getStatus()))
            .sorted((a, b) -> {
                if (a.getCalledAt() == null) return 1;
                if (b.getCalledAt() == null) return -1;
                return b.getCalledAt().compareTo(a.getCalledAt());
            })
            .collect(Collectors.toList());

        List<ScreenDataResponse.CurrentCallVO> currentCalls = active.stream().map(t -> {
            ScreenDataResponse.CurrentCallVO vo = new ScreenDataResponse.CurrentCallVO();
            vo.setTicketNo(t.getTicketNo());
            Counter c = t.getCounterId() != null ? counterMap.get(t.getCounterId()) : null;
            vo.setCounterName(c != null ? c.getName() : "");
            vo.setCalledAt(t.getCalledAt() != null ? t.getCalledAt().toLocalTime().toString() : "");
            BusinessType bt = btMap.get(t.getBusinessTypeId());
            vo.setBusinessTypeName(bt != null ? bt.getName() : "");
            return vo;
        }).collect(Collectors.toList());
        resp.setCurrentCalls(currentCalls);

        // waitingQueue: 等待中的票列表
        List<ScreenDataResponse.WaitingTicketVO> waitingQueue = finalTodayTickets.stream()
            .filter(t -> "waiting".equals(t.getStatus()))
            .sorted((a, b) -> {
                if (a.getCreatedAt() == null) return 1;
                if (b.getCreatedAt() == null) return -1;
                return a.getCreatedAt().compareTo(b.getCreatedAt());
            })
            .limit(100)
            .map(t -> {
                ScreenDataResponse.WaitingTicketVO vo = new ScreenDataResponse.WaitingTicketVO();
                vo.setId(t.getId());
                vo.setTicketNo(t.getTicketNo());
                BusinessType bt = btMap.get(t.getBusinessTypeId());
                vo.setBusinessTypeName(bt != null ? bt.getName() : "");
                vo.setReactivated(t.getReactivatedAt() != null);
                return vo;
            })
            .collect(Collectors.toList());
        resp.setWaitingQueue(waitingQueue);

        // counters list
        List<ScreenDataResponse.CounterStatusVO> counterVOs = counters.stream().map(c -> {
            ScreenDataResponse.CounterStatusVO vo = new ScreenDataResponse.CounterStatusVO();
            vo.setName(c.getName());
            vo.setStatus(c.getStatus());
            if (c.getCurrentTicketId() != null) {
                Ticket cur = ticketMap.get(c.getCurrentTicketId());
                // 只有 called/serving 状态的票才显示票号，已完成/跳过/取消的不显示
                if (cur != null && ("called".equals(cur.getStatus()) || "serving".equals(cur.getStatus()))) {
                    vo.setCurrentTicketNo(cur.getTicketNo());
                } else {
                    vo.setCurrentTicketNo("");
                }
            } else {
                vo.setCurrentTicketNo("");
            }
            return vo;
        }).collect(Collectors.toList());
        resp.setCounters(counterVOs);

        // recentCalls: 仅返回窗口人员主动跳过的票号
        // 规则：状态为 skipped，且必须已经被窗口叫过号（calledAt != null）
        List<Ticket> recent = finalTodayTickets.stream()
            .filter(t -> "skipped".equals(t.getStatus()) && t.getCalledAt() != null && t.getCounterId() != null)
            .sorted((a, b) -> b.getCalledAt().compareTo(a.getCalledAt()))
            .limit(20)
            .collect(Collectors.toList());

        List<ScreenDataResponse.RecentCallVO> recentCalls = recent.stream().map(t -> {
            ScreenDataResponse.RecentCallVO vo = new ScreenDataResponse.RecentCallVO();
            vo.setTicketNo(t.getTicketNo());
            Counter c = t.getCounterId() != null ? counterMap.get(t.getCounterId()) : null;
            vo.setCounterName(c != null ? c.getName() : "");
            vo.setCalledAt(t.getCalledAt() != null ? t.getCalledAt().toLocalTime().toString() : "");
            BusinessType bt = btMap.get(t.getBusinessTypeId());
            vo.setBusinessTypeName(bt != null ? bt.getName() : "");
            return vo;
        }).collect(Collectors.toList());
        resp.setRecentCalls(recentCalls);

        // stats
        ScreenDataResponse.ScreenStatsVO stats = new ScreenDataResponse.ScreenStatsVO();
        stats.setTotalToday((int) finalTodayTickets.size());
        stats.setWaitingCount((int) finalTodayTickets.stream().filter(t -> "waiting".equals(t.getStatus())).count());
        stats.setCompletedCount((int) finalTodayTickets.stream().filter(t -> "completed".equals(t.getStatus())).count());
        stats.setSkippedCount((int) finalTodayTickets.stream().filter(t -> "skipped".equals(t.getStatus())).count());
        resp.setStats(stats);

        // 公告和区域名：按 regionCode 获取
        Region regionObj = null;
        if (regionCode != null && !regionCode.isEmpty()) {
            regionObj = regionMapper.selectOne(new QueryWrapper<Region>().eq("region_code", regionCode));
            if (regionObj != null) {
                if (regionObj.getAnnouncementText() != null) {
                    resp.setAnnouncementText(regionObj.getAnnouncementText());
                }
                if (regionObj.getRegionName() != null) {
                    resp.setRegionName(regionObj.getRegionName());
                }
                resp.setRegionId(regionObj.getId());
                resp.setRegionCode(regionObj.getRegionCode());
            }
        }

        return Result.ok(resp);
    }

    @GetMapping("/admin/dashboard")
    public Result<DashboardResponse> getDashboard(@RequestParam(required = false) Long userId) {
        DashboardResponse resp = new DashboardResponse();

        // 区域权限过滤：通过 regionId 直接过滤
        Set<Long> allowedRegionIds = null;
        if (userId != null && userId > 0) {
            SysUser user = sysUserMapper.selectById(userId);
            if (user != null && !"SUPER_ADMIN".equals(user.getRole())) {
                if (user.getRegionCode() != null && !user.getRegionCode().isEmpty()) {
                    Region userRegion = regionService.getByCode(user.getRegionCode());
                    if (userRegion != null) {
                        List<Long> descendantRegionIds = regionService.getDescendantRegionIds(userRegion.getId());
                        allowedRegionIds = new HashSet<>(descendantRegionIds);
                    } else {
                        allowedRegionIds = Collections.emptySet();
                    }
                } else {
                    allowedRegionIds = Collections.emptySet();
                }
            }
            // SUPER_ADMIN: allowedRegionIds = null，不过滤
        }
        final Set<Long> regionFilter = allowedRegionIds;

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        final List<Ticket> finalTodayTickets;
        if (regionFilter != null && regionFilter.isEmpty()) {
            finalTodayTickets = Collections.emptyList();
        } else {
            QueryWrapper<Ticket> qw = new QueryWrapper<Ticket>()
                .ge("created_at", startOfDay)
                .lt("created_at", endOfDay);
            if (regionFilter != null) {
                qw.in("region_id", regionFilter);
            }
            finalTodayTickets = ticketMapper.selectList(qw);
        }

        resp.setTotalTickets(finalTodayTickets.size());
        resp.setCompletedCount((int) finalTodayTickets.stream().filter(t -> "completed".equals(t.getStatus())).count());
        resp.setWaitingCount((int) finalTodayTickets.stream().filter(t -> "waiting".equals(t.getStatus())).count());

        // avgWaitMinutes: for COMPLETED/SKIPPED tickets where calledAt is not null
        List<Ticket> completedOrSkipped = finalTodayTickets.stream()
            .filter(t -> ("completed".equals(t.getStatus()) || "skipped".equals(t.getStatus())) && t.getCalledAt() != null && t.getCreatedAt() != null)
            .collect(Collectors.toList());
        if (!completedOrSkipped.isEmpty()) {
            double avgWait = completedOrSkipped.stream()
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getCalledAt()).toMinutes())
                .average().orElse(0.0);
            resp.setAvgWaitMinutes(Math.round(avgWait * 10.0) / 10.0);
        } else {
            resp.setAvgWaitMinutes(0.0);
        }

        // avgServeMinutes: for COMPLETED tickets where servedAt is not null
        List<Ticket> completedWithServe = finalTodayTickets.stream()
            .filter(t -> "completed".equals(t.getStatus()) && t.getServedAt() != null && t.getCompletedAt() != null)
            .collect(Collectors.toList());
        if (!completedWithServe.isEmpty()) {
            double avgServe = completedWithServe.stream()
                .mapToLong(t -> Duration.between(t.getServedAt(), t.getCompletedAt()).toMinutes())
                .average().orElse(0.0);
            resp.setAvgServeMinutes(Math.round(avgServe * 10.0) / 10.0);
        } else {
            resp.setAvgServeMinutes(0.0);
        }

        // counter stats（仅显示有权访问的柜台）
        List<Counter> counters;
        if (regionFilter != null && !regionFilter.isEmpty()) {
            counters = counterMapper.selectList(
                new QueryWrapper<Counter>().in("region_id", regionFilter));
        } else if (regionFilter != null) {
            counters = Collections.emptyList();
        } else {
            counters = counterMapper.selectList(new QueryWrapper<Counter>());
        }
        List<DashboardResponse.CounterStatVO> counterStats = counters.stream().map(c -> {
            DashboardResponse.CounterStatVO vo = new DashboardResponse.CounterStatVO();
            vo.setCounterName(c.getName());
            vo.setCompletedCount((int) finalTodayTickets.stream()
                .filter(t -> "completed".equals(t.getStatus()) && c.getId().equals(t.getCounterId()))
                .count());
            return vo;
        }).collect(Collectors.toList());
        resp.setCounterStats(counterStats);

        // business type stats
        List<BusinessType> businessTypes = businessTypeMapper.selectList(new QueryWrapper<BusinessType>());
        List<DashboardResponse.BusinessTypeStatVO> btStats = businessTypes.stream().map(bt -> {
            DashboardResponse.BusinessTypeStatVO vo = new DashboardResponse.BusinessTypeStatVO();
            vo.setBusinessTypeId(bt.getId());
            vo.setBusinessType(bt.getName());
            int count = (int) finalTodayTickets.stream().filter(t -> bt.getId().equals(t.getBusinessTypeId())).count();
            vo.setCount(count);
            double pct = finalTodayTickets.isEmpty() ? 0.0 : Math.round(count * 1000.0 / finalTodayTickets.size()) / 10.0;
            vo.setPercentage(pct);
            return vo;
        }).collect(Collectors.toList());
        resp.setBusinessTypeStats(btStats);

        return Result.ok(resp);
    }
}
