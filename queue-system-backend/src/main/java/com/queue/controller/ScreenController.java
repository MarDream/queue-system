package com.queue.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.queue.common.Result;
import com.queue.dto.DashboardResponse;
import com.queue.dto.DashboardOverviewRow;
import com.queue.dto.ScreenDataResponse;
import com.queue.dto.ScreenCounterStatusRow;
import com.queue.dto.ScreenCurrentCallRow;
import com.queue.dto.ScreenRecentCallRow;
import com.queue.dto.ScreenStatsRow;
import com.queue.dto.ScreenWaitingTicketRow;
import com.queue.entity.Region;
import com.queue.entity.SysUser;
import com.queue.mapper.AnalyticsMapper;
import com.queue.mapper.RegionMapper;
import com.queue.service.AuthContextService;
import com.queue.service.RegionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
public class ScreenController {

    private final AnalyticsMapper analyticsMapper;
    private final RegionMapper regionMapper;
    private final RegionService regionService;
    private final AuthContextService authContextService;

    public ScreenController(AnalyticsMapper analyticsMapper,
                           RegionMapper regionMapper,
                           RegionService regionService,
                           AuthContextService authContextService) {
        this.analyticsMapper = analyticsMapper;
        this.regionMapper = regionMapper;
        this.regionService = regionService;
        this.authContextService = authContextService;
    }

    @GetMapping("/queue/screen")
    public Result<ScreenDataResponse> getScreenData(@RequestParam(required = false) String regionCode) {
        ScreenDataResponse resp = new ScreenDataResponse();

        Set<Long> regionFilter = resolveRegionFilter(regionCode);

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        if (regionFilter != null && regionFilter.isEmpty()) {
            resp.setCurrentCalls(Collections.emptyList());
            resp.setWaitingQueue(Collections.emptyList());
            resp.setCounters(Collections.emptyList());
            resp.setRecentCalls(Collections.emptyList());
            resp.setStats(new ScreenDataResponse.ScreenStatsVO(0, 0, 0, 0));
        } else {
            List<ScreenDataResponse.CurrentCallVO> currentCalls = analyticsMapper.selectScreenCurrentCalls(regionFilter, startOfDay, endOfDay)
                .stream()
                .map(this::toCurrentCall)
                .toList();
            resp.setCurrentCalls(currentCalls);

            List<ScreenDataResponse.WaitingTicketVO> waitingQueue = analyticsMapper.selectScreenWaitingQueue(regionFilter, startOfDay, endOfDay, 100)
                .stream()
                .map(this::toWaitingTicket)
                .toList();
            resp.setWaitingQueue(waitingQueue);

            List<ScreenDataResponse.CounterStatusVO> counterVOs = analyticsMapper.selectScreenCounterStatuses(regionFilter)
                .stream()
                .map(this::toCounterStatus)
                .toList();
            resp.setCounters(counterVOs);

            List<ScreenDataResponse.RecentCallVO> recentCalls = analyticsMapper.selectScreenRecentCalls(regionFilter, startOfDay, endOfDay, 20)
                .stream()
                .map(this::toRecentCall)
                .toList();
            resp.setRecentCalls(recentCalls);

            ScreenStatsRow statsRow = analyticsMapper.selectScreenStats(regionFilter, startOfDay, endOfDay);
            ScreenDataResponse.ScreenStatsVO stats = new ScreenDataResponse.ScreenStatsVO();
            stats.setTotalToday(statsRow == null || statsRow.getTotalToday() == null ? 0 : statsRow.getTotalToday().intValue());
            stats.setWaitingCount(statsRow == null || statsRow.getWaitingCount() == null ? 0 : statsRow.getWaitingCount().intValue());
            stats.setCompletedCount(statsRow == null || statsRow.getCompletedCount() == null ? 0 : statsRow.getCompletedCount().intValue());
            stats.setSkippedCount(statsRow == null || statsRow.getSkippedCount() == null ? 0 : statsRow.getSkippedCount().intValue());
            resp.setStats(stats);
        }

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
    public Result<DashboardResponse> getDashboard(HttpServletRequest request) {
        DashboardResponse resp = new DashboardResponse();

        SysUser currentUser = authContextService.requireCurrentUser(request);
        Set<Long> regionFilter = authContextService.resolveAllowedRegionIds(currentUser);

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        if (regionFilter != null && regionFilter.isEmpty()) {
            resp.setTotalTickets(0);
            resp.setCompletedCount(0);
            resp.setWaitingCount(0);
            resp.setAvgWaitMinutes(0.0);
            resp.setAvgServeMinutes(0.0);
            resp.setCounterStats(Collections.emptyList());
            resp.setBusinessTypeStats(Collections.emptyList());
            return Result.ok(resp);
        }

        DashboardOverviewRow overview = analyticsMapper.selectDashboardOverview(regionFilter, startOfDay, endOfDay);
        int totalTickets = overview == null || overview.getTotalTickets() == null ? 0 : overview.getTotalTickets().intValue();
        resp.setTotalTickets(totalTickets);
        resp.setCompletedCount(overview == null || overview.getCompletedCount() == null ? 0 : overview.getCompletedCount().intValue());
        resp.setWaitingCount(overview == null || overview.getWaitingCount() == null ? 0 : overview.getWaitingCount().intValue());
        resp.setAvgWaitMinutes(overview == null || overview.getAvgWaitMinutes() == null ? 0.0 : overview.getAvgWaitMinutes());
        resp.setAvgServeMinutes(overview == null || overview.getAvgServeMinutes() == null ? 0.0 : overview.getAvgServeMinutes());

        resp.setCounterStats(analyticsMapper.selectDashboardCounterStats(regionFilter, startOfDay, endOfDay)
            .stream()
            .map(row -> new DashboardResponse.CounterStatVO(
                row.getCounterName(),
                row.getCompletedCount() == null ? 0 : row.getCompletedCount().intValue()))
            .toList());

        resp.setBusinessTypeStats(analyticsMapper.selectDashboardBusinessTypeStats(regionFilter, startOfDay, endOfDay)
            .stream()
            .map(row -> {
                int count = row.getCount() == null ? 0 : row.getCount().intValue();
                double percentage = totalTickets == 0 ? 0.0 : Math.round(count * 1000.0 / totalTickets) / 10.0;
                return new DashboardResponse.BusinessTypeStatVO(
                    row.getBusinessTypeId(),
                    row.getBusinessType(),
                    count,
                    percentage
                );
            })
            .toList());

        return Result.ok(resp);
    }

    private Set<Long> resolveRegionFilter(String regionCode) {
        if (regionCode == null || regionCode.isEmpty()) {
            return null;
        }
        Region region = regionMapper.selectOne(new QueryWrapper<Region>().eq("region_code", regionCode));
        if (region == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(regionService.getDescendantRegionIds(region.getId()));
    }

    private ScreenDataResponse.CurrentCallVO toCurrentCall(ScreenCurrentCallRow row) {
        ScreenDataResponse.CurrentCallVO vo = new ScreenDataResponse.CurrentCallVO();
        vo.setTicketNo(row.getTicketNo());
        vo.setCounterId(row.getCounterId() == null ? null : String.valueOf(row.getCounterId()));
        vo.setCounterName(row.getCounterName() != null ? row.getCounterName() : "");
        vo.setCalledAt(row.getCalledAt() != null ? row.getCalledAt().toLocalTime().toString() : "");
        vo.setBusinessTypeName(row.getBusinessTypeName() != null ? row.getBusinessTypeName() : "");
        return vo;
    }

    private ScreenDataResponse.WaitingTicketVO toWaitingTicket(ScreenWaitingTicketRow row) {
        ScreenDataResponse.WaitingTicketVO vo = new ScreenDataResponse.WaitingTicketVO();
        vo.setId(row.getId());
        vo.setTicketNo(row.getTicketNo());
        vo.setBusinessTypeName(row.getBusinessTypeName() != null ? row.getBusinessTypeName() : "");
        vo.setReactivated(Boolean.TRUE.equals(row.getReactivated()));
        return vo;
    }

    private ScreenDataResponse.CounterStatusVO toCounterStatus(ScreenCounterStatusRow row) {
        ScreenDataResponse.CounterStatusVO vo = new ScreenDataResponse.CounterStatusVO();
        vo.setName(row.getName());
        vo.setStatus(row.getStatus());
        vo.setCurrentTicketNo(row.getCurrentTicketNo() != null ? row.getCurrentTicketNo() : "");
        return vo;
    }

    private ScreenDataResponse.RecentCallVO toRecentCall(ScreenRecentCallRow row) {
        ScreenDataResponse.RecentCallVO vo = new ScreenDataResponse.RecentCallVO();
        vo.setTicketNo(row.getTicketNo());
        vo.setCounterName(row.getCounterName() != null ? row.getCounterName() : "");
        vo.setCalledAt(row.getCalledAt() != null ? row.getCalledAt().toLocalTime().toString() : "");
        vo.setBusinessTypeName(row.getBusinessTypeName() != null ? row.getBusinessTypeName() : "");
        return vo;
    }
}
