package com.queue.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CounterRecentServiceRow {
    private String ticketNo;
    private String businessTypeName;
    private String customerName;
    private String status;
    private LocalDateTime calledAt;
    private LocalDateTime completedAt;
    private Double serviceMinutes;
}
