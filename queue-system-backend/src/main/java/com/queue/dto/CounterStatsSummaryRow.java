package com.queue.dto;

import lombok.Data;

@Data
public class CounterStatsSummaryRow {
    private Long todayServedCount;
    private Long todayCalledCount;
    private Long todaySkippedCount;
    private Double avgServiceMinutes;
    private Double avgWaitMinutes;
}
