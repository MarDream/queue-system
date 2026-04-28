package com.queue.controller;

import com.queue.common.BusinessException;
import com.queue.common.Result;
import com.queue.dto.CounterOperationRequest;
import com.queue.dto.CounterSnapshotResponse;
import com.queue.dto.CounterStatsSummaryRow;
import com.queue.dto.ScreenWaitingTicketRow;
import com.queue.entity.Counter;
import com.queue.entity.SysUser;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CounterControllerTest {

    @Mock
    private CounterService counterService;
    @Mock
    private CounterMapper counterMapper;
    @Mock
    private CounterBusinessMapper counterBusinessMapper;
    @Mock
    private TicketMapper ticketMapper;
    @Mock
    private BusinessTypeMapper businessTypeMapper;
    @Mock
    private AnalyticsMapper analyticsMapper;
    @Mock
    private RegionService regionService;
    @Mock
    private CounterOperatorMapper counterOperatorMapper;
    @Mock
    private AuthContextService authContextService;
    @Mock
    private HttpServletRequest httpServletRequest;

    private CounterController controller;

    @BeforeEach
    void setUp() {
        controller = new CounterController(
                counterService,
                counterMapper,
                counterBusinessMapper,
                ticketMapper,
                businessTypeMapper,
                analyticsMapper,
                regionService,
                counterOperatorMapper,
                authContextService
        );
    }

    @Test
    void windowOperatorCannotOperateUnassignedCounter() {
        SysUser user = new SysUser();
        user.setId(11L);
        user.setRole("WINDOW_OPERATOR");

        Counter counter = new Counter();
        counter.setId(8L);
        counter.setRegionId(3L);

        CounterOperationRequest request = new CounterOperationRequest();
        request.setCounterId(8L);

        when(authContextService.requireCurrentUser(httpServletRequest)).thenReturn(user);
        when(counterMapper.selectById(8L)).thenReturn(counter);
        when(counterOperatorMapper.selectCounterIdsByUserId(11L)).thenReturn(List.of(9L));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.callNext(request, httpServletRequest)
        );

        assertEquals(403, exception.getCode());
        assertEquals("无权限操作该窗口", exception.getMessage());
        verify(authContextService).assertRegionAccess(user, 3L);
        verify(counterService, never()).callNext(anyLong());
    }

    @Test
    void assignedWindowOperatorCanFetchSnapshotWithWaitingMetadata() {
        SysUser user = new SysUser();
        user.setId(11L);
        user.setRole("WINDOW_OPERATOR");

        Counter counter = new Counter();
        counter.setId(8L);
        counter.setRegionId(3L);
        counter.setName("1号窗口");
        counter.setStatus("idle");

        CounterStatsSummaryRow summary = new CounterStatsSummaryRow();
        summary.setTodayServedCount(5L);

        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 28, 9, 30, 0);
        ScreenWaitingTicketRow waitingRow = new ScreenWaitingTicketRow();
        waitingRow.setId(101L);
        waitingRow.setTicketNo("440305A001");
        waitingRow.setBusinessTypeName("社保");
        waitingRow.setReactivated(true);
        waitingRow.setCreatedAt(createdAt);

        List<Long> regionIds = List.of(3L, 31L);
        List<Long> businessTypeIds = List.of(101L);

        when(authContextService.requireCurrentUser(httpServletRequest)).thenReturn(user);
        when(counterMapper.selectById(8L)).thenReturn(counter);
        when(counterOperatorMapper.selectCounterIdsByUserId(11L)).thenReturn(List.of(8L));
        when(counterBusinessMapper.selectBusinessTypeIdsByCounterId(8L)).thenReturn(businessTypeIds);
        when(regionService.getDescendantRegionIds(3L)).thenReturn(regionIds);
        when(analyticsMapper.selectCounterStatsSummary(eq(8L), any(), any())).thenReturn(summary);
        when(analyticsMapper.countCounterWaitingTickets(eq(regionIds), eq(businessTypeIds), any(), any())).thenReturn(2L);
        when(analyticsMapper.selectCounterWaitingQueue(eq(regionIds), eq(businessTypeIds), any(), any(), eq(20)))
                .thenReturn(List.of(waitingRow));

        Result<CounterSnapshotResponse> result = controller.getSnapshot(8L, httpServletRequest);

        assertEquals(200, result.getCode());
        assertEquals(5, result.getData().getTodayCompletedCount());
        assertEquals(2, result.getData().getWaitingCount());
        assertEquals(1, result.getData().getWaitingQueue().size());
        assertTrue(result.getData().getWaitingQueue().get(0).getReactivated());
        assertEquals(createdAt, result.getData().getWaitingQueue().get(0).getCreatedAt());
        verify(authContextService).assertRegionAccess(user, 3L);
    }
}
