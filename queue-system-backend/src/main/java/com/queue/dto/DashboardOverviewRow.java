package com.queue.dto;

import lombok.Data;

@Data
public class DashboardOverviewRow {
    private Long totalTickets;
    private Long completedCount;
    private Long waitingCount;
    private Double avgWaitMinutes;
    private Double avgServeMinutes;
}
