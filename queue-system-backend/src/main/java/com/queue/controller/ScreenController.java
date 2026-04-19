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
import java.time.LocalTime;
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

        // Query today's tickets (按 regionFilter 过滤)
        List<Ticket> unfilteredTickets = ticketMapper.selectList(
            new QueryWrapper<Ticket>().apply("DATE(created_at) = CURDATE()")
        );
        List<Ticket> finalTodayTickets;
        if (finalRegionFilter != null && !finalRegionFilter.isEmpty()) {
            finalTodayTickets = unfilteredTickets.stream()
                .filter(t -> t.getRegionId() != null && finalRegionFilter.contains(t.getRegionId()))
                .collect(Collectors.toList());
        } else if (finalRegionFilter != null) {
            finalTodayTickets = Collections.emptyList();
        } else {
            finalTodayTickets = unfilteredTickets;
        }

        // Counters (按 regionFilter 过滤)
        List<Counter> allCounters = counterMapper.selectList(new QueryWrapper<Counter>());
        List<Counter> counters;
        if (finalRegionFilter != null && !finalRegionFilter.isEmpty()) {
            counters = allCounters.stream()
                .filter(c -> c.getRegionId() != null && finalRegionFilter.contains(c.getRegionId()))
                .collect(Collectors.toList());
        } else if (finalRegionFilter != null) {
            counters = Collections.emptyList();
        } else {
            counters = allCounters;
        }

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
            Counter c = counters.stream().filter(cn -> cn.getId().equals(t.getCounterId())).findFirst().orElse(null);
            vo.setCounterName(c != null ? c.getName() : "");
            vo.setCalledAt(t.getCalledAt() != null ? t.getCalledAt().toLocalTime().toString() : "");
            BusinessType bt = businessTypeMapper.selectById(t.getBusinessTypeId());
            vo.setBusinessTypeName(bt != null ? bt.getName() : "");
            return vo;
        }).collect(Collectors.toList());
        resp.setCurrentCalls(currentCalls);

        // counters list
        List<ScreenDataResponse.CounterStatusVO> counterVOs = counters.stream().map(c -> {
            ScreenDataResponse.CounterStatusVO vo = new ScreenDataResponse.CounterStatusVO();
            vo.setName(c.getName());
            vo.setStatus(c.getStatus());
            if (c.getCurrentTicketId() != null) {
                Ticket cur = finalTodayTickets.stream().filter(t -> t.getId().equals(c.getCurrentTicketId())).findFirst().orElse(null);
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

        // recentCalls: last 20 called/serving/completed/skipped
        List<Ticket> recent = finalTodayTickets.stream()
            .filter(t -> t.getCalledAt() != null)
            .sorted((a, b) -> b.getCalledAt().compareTo(a.getCalledAt()))
            .limit(20)
            .collect(Collectors.toList());

        List<ScreenDataResponse.RecentCallVO> recentCalls = recent.stream().map(t -> {
            ScreenDataResponse.RecentCallVO vo = new ScreenDataResponse.RecentCallVO();
            vo.setTicketNo(t.getTicketNo());
            Counter c = counters.stream().filter(cn -> cn.getId().equals(t.getCounterId())).findFirst().orElse(null);
            vo.setCounterName(c != null ? c.getName() : "");
            vo.setCalledAt(t.getCalledAt() != null ? t.getCalledAt().toLocalTime().toString() : "");
            BusinessType bt = businessTypeMapper.selectById(t.getBusinessTypeId());
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

        // 公告：按 regionCode 获取
        if (regionCode != null && !regionCode.isEmpty()) {
            Region region = regionMapper.selectOne(new QueryWrapper<Region>().eq("region_code", regionCode));
            if (region != null && region.getAnnouncementText() != null) {
                resp.setAnnouncementText(region.getAnnouncementText());
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

        List<Ticket> unfilteredTickets = ticketMapper.selectList(
            new QueryWrapper<Ticket>().apply("DATE(created_at) = CURDATE()")
        );
        // 按区域过滤（直接通过 regionId 过滤，等待中的票 counterId=null 也参与过滤）
        final List<Ticket> finalTodayTickets;
        if (regionFilter != null && !regionFilter.isEmpty()) {
            finalTodayTickets = unfilteredTickets.stream()
                .filter(t -> t.getRegionId() != null && regionFilter.contains(t.getRegionId()))
                .collect(Collectors.toList());
        } else if (regionFilter != null) {
            finalTodayTickets = Collections.emptyList();
        } else {
            finalTodayTickets = unfilteredTickets;
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
