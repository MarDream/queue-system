package com.queue.service.impl;

import com.queue.entity.Counter;
import com.queue.entity.Ticket;
import com.queue.mapper.BusinessTypeMapper;
import com.queue.mapper.CounterBusinessMapper;
import com.queue.mapper.CounterMapper;
import com.queue.mapper.RegionMapper;
import com.queue.mapper.TicketMapper;
import com.queue.service.QueueService;
import com.queue.service.RegionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CounterServiceImplTest {

    @Mock
    private CounterMapper counterMapper;
    @Mock
    private CounterBusinessMapper counterBusinessMapper;
    @Mock
    private TicketMapper ticketMapper;
    @Mock
    private QueueService queueService;
    @Mock
    private BusinessTypeMapper businessTypeMapper;
    @Mock
    private RegionMapper regionMapper;
    @Mock
    private RegionService regionService;
    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private CounterServiceImpl counterService;

    @BeforeEach
    void setUp() {
        counterService = new CounterServiceImpl(
                counterMapper,
                counterBusinessMapper,
                ticketMapper,
                queueService,
                businessTypeMapper,
                regionMapper,
                regionService,
                objectMapper
        );
    }

    @Test
    void callNextResetsStaleBusyCounterBeforeCheckingOperability() {
        Counter counter = new Counter();
        counter.setId(8L);
        counter.setRegionId(3L);
        counter.setStatus("busy");
        counter.setCurrentTicketId(101L);

        Ticket completedTicket = new Ticket();
        completedTicket.setId(101L);
        completedTicket.setStatus("completed");

        when(queueService.acquireLock("call:8", 5)).thenReturn(true);
        when(counterMapper.selectById(8L)).thenReturn(counter);
        when(ticketMapper.selectById(101L)).thenReturn(completedTicket);
        when(counterBusinessMapper.selectBusinessTypeIdsByCounterId(8L)).thenReturn(Collections.emptyList());

        assertNull(counterService.callNext(8L));

        verify(counterMapper).updateById(counter);
        verify(queueService).releaseLock("call:8");
    }
}
