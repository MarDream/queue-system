package com.queue.dto;

import lombok.Data;

@Data
public class ScreenStatsRow {
    private Long totalToday;
    private Long waitingCount;
    private Long completedCount;
    private Long skippedCount;
}
