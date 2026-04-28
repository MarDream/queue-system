package com.queue.controller;

import com.queue.common.Result;
import com.queue.common.BusinessException;
import com.queue.dto.CounterSnapshotResponse;
import com.queue.dto.CounterCallResponse;
import com.queue.dto.CounterOperationRequest;
import com.queue.dto.CounterStatsSummaryRow;
import com.queue.dto.ScreenWaitingTicketRow;
import com.queue.entity.BusinessType;
import com.queue.entity.Counter;
import com.queue.entity.SysUser;
import com.queue.entity.Ticket;
import com.queue.enums.CounterStatus;
import com.queue.enums.TicketStatus;
import com.queue.mapper.AnalyticsMapper;
import com.queue.mapper.BusinessTypeMapper;
import com.queue.mapper.CounterBusinessMapper;
import com.queue.mapper.CounterMapper;
import com.queue.mapper.CounterOperatorMapper;
import com.queue.mapper.TicketMapper;
import com.queue.service.AuthContextService;
import com.queue.service.CounterService;
import com.queue.service.RegionService;
import jakarta.servlet.http.HttpServletRequest;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v1/counter")
public class CounterController {

    private final CounterService counterService;
    private final CounterMapper counterMapper;
    private final CounterBusinessMapper counterBusinessMapper;
    private final TicketMapper ticketMapper;
    private final BusinessTypeMapper businessTypeMapper;
    private final AnalyticsMapper analyticsMapper;
    private final RegionService regionService;
    private final CounterOperatorMapper counterOperatorMapper;
    private final AuthContextService authContextService;

    public CounterController(CounterService counterService,
                             CounterMapper counterMapper,
                             CounterBusinessMapper counterBusinessMapper,
                             TicketMapper ticketMapper,
                             BusinessTypeMapper businessTypeMapper,
                             AnalyticsMapper analyticsMapper,
                             RegionService regionService,
                             CounterOperatorMapper counterOperatorMapper,
                             AuthContextService authContextService) {
        this.counterService = counterService;
        this.counterMapper = counterMapper;
        this.counterBusinessMapper = counterBusinessMapper;
        this.ticketMapper = ticketMapper;
        this.businessTypeMapper = businessTypeMapper;
        this.analyticsMapper = analyticsMapper;
        this.regionService = regionService;
        this.counterOperatorMapper = counterOperatorMapper;
        this.authContextService = authContextService;
    }

    @PostMapping("/call/next")
    public Result<CounterCallResponse> callNext(@RequestBody CounterOperationRequest request,
                                                HttpServletRequest httpRequest) {
        requireCounterAccess(request.getCounterId(), httpRequest);
        CounterCallResponse result = counterService.callNext(request.getCounterId());
        return Result.ok(result, result == null ? "暂无等待客户" : null);
    }

    @PostMapping("/call/recall")
    public Result<CounterCallResponse> recall(@RequestBody CounterOperationRequest request,
                                              HttpServletRequest httpRequest) {
        requireCounterAccess(request.getCounterId(), httpRequest);
        return Result.ok(counterService.recall(request.getCounterId()));
    }

    @PostMapping("/call/skip")
    public Result<Void> skip(@RequestBody CounterOperationRequest request,
                             HttpServletRequest httpRequest) {
        requireCounterAccess(request.getCounterId(), httpRequest);
        counterService.skip(request.getCounterId());
        return Result.ok();
    }

    @PostMapping("/serve")
    public Result<Void> serve(@RequestBody CounterOperationRequest request,
                              HttpServletRequest httpRequest) {
        requireCounterAccess(request.getCounterId(), httpRequest);
        counterService.serve(request.getCounterId());
        return Result.ok();
    }

    @PostMapping("/complete")
    public Result<Void> complete(@RequestBody CounterOperationRequest request,
                                 HttpServletRequest httpRequest) {
        requireCounterAccess(request.getCounterId(), httpRequest);
        counterService.complete(request.getCounterId());
        return Result.ok();
    }

    @PostMapping("/toggle-pause")
    public Result<Void> togglePause(@RequestBody CounterOperationRequest request,
                                    HttpServletRequest httpRequest) {
        requireCounterAccess(request.getCounterId(), httpRequest);
        counterService.togglePause(request.getCounterId());
        return Result.ok();
    }

    @PostMapping("/reactivate")
    public Result<Void> reactivate(@RequestBody ReactivateRequest request,
                                   HttpServletRequest httpRequest) {
        requireTicketAccess(request.getTicketNo(), httpRequest);
        counterService.reactivateSkippedTicket(request.getTicketNo());
        return Result.ok();
    }

    @GetMapping("/snapshot")
    public Result<CounterSnapshotResponse> getSnapshot(@RequestParam Long counterId,
                                                       HttpServletRequest httpRequest) {
        Counter counter = requireCounterAccess(counterId, httpRequest);

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        List<Long> businessTypeIds = counterBusinessMapper.selectBusinessTypeIdsByCounterId(counterId);
        List<Long> regionIds = regionService.getDescendantRegionIds(counter.getRegionId());

        CounterSnapshotResponse response = new CounterSnapshotResponse();
        response.setCounterId(counter.getId());
        response.setCounterName(counter.getName());
        response.setCurrentStatus(counter.getStatus());
        response.setPaused(CounterStatus.PAUSED.getValue().equals(counter.getStatus()));

        if (counter.getCurrentTicketId() != null) {
            Ticket currentTicket = ticketMapper.selectById(counter.getCurrentTicketId());
            if (currentTicket != null && (TicketStatus.CALLED.getValue().equals(currentTicket.getStatus())
                    || TicketStatus.SERVING.getValue().equals(currentTicket.getStatus()))) {
                response.setCurrentTicketNo(currentTicket.getTicketNo());
                response.setCurrentTicketStatus(currentTicket.getStatus());
                BusinessType businessType = businessTypeMapper.selectById(currentTicket.getBusinessTypeId());
                response.setCurrentBusinessTypeName(businessType != null ? businessType.getName() : "");
            }
        }

        CounterStatsSummaryRow summary = analyticsMapper.selectCounterStatsSummary(counterId, startOfDay, endOfDay);
        response.setTodayCompletedCount(summary == null || summary.getTodayServedCount() == null
                ? 0
                : summary.getTodayServedCount().intValue());

        if (businessTypeIds == null || businessTypeIds.isEmpty() || regionIds == null || regionIds.isEmpty()) {
            response.setWaitingCount(0);
            response.setWaitingQueue(Collections.emptyList());
            return Result.ok(response);
        }

        Long waitingCount = analyticsMapper.countCounterWaitingTickets(regionIds, businessTypeIds, startOfDay, endOfDay);
        response.setWaitingCount(waitingCount == null ? 0 : waitingCount.intValue());
        response.setWaitingQueue(analyticsMapper.selectCounterWaitingQueue(regionIds, businessTypeIds, startOfDay, endOfDay, 20)
                .stream()
                .map(this::toWaitingTicket)
                .toList());
        return Result.ok(response);
    }

    private CounterSnapshotResponse.WaitingTicketVO toWaitingTicket(ScreenWaitingTicketRow row) {
        CounterSnapshotResponse.WaitingTicketVO vo = new CounterSnapshotResponse.WaitingTicketVO();
        vo.setId(row.getId());
        vo.setTicketNo(row.getTicketNo());
        vo.setBusinessTypeName(row.getBusinessTypeName());
        vo.setReactivated(Boolean.TRUE.equals(row.getReactivated()));
        vo.setCreatedAt(row.getCreatedAt());
        return vo;
    }

    private Counter requireCounterAccess(Long counterId, HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        Counter counter = counterMapper.selectById(counterId);
        if (counter == null) {
            throw new BusinessException(400, "窗口不存在");
        }
        authContextService.assertRegionAccess(currentUser, counter.getRegionId());
        if ("WINDOW_OPERATOR".equals(currentUser.getRole())) {
            List<Long> assignedCounterIds = counterOperatorMapper.selectCounterIdsByUserId(currentUser.getId());
            if (assignedCounterIds == null || !assignedCounterIds.contains(counterId)) {
                throw new BusinessException(403, "无权限操作该窗口");
            }
        }
        return counter;
    }

    private void requireTicketAccess(String ticketNo, HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        Ticket ticket = ticketMapper.selectOne(new QueryWrapper<Ticket>()
                .eq("ticket_no", ticketNo)
                .last("LIMIT 1"));
        if (ticket == null) {
            throw new BusinessException(40003, "票号不存在");
        }
        authContextService.assertRegionAccess(currentUser, ticket.getRegionId());
    }

    public static class ReactivateRequest {
        private String ticketNo;

        public String getTicketNo() {
            return ticketNo;
        }

        public void setTicketNo(String ticketNo) {
            this.ticketNo = ticketNo;
        }
    }
}
