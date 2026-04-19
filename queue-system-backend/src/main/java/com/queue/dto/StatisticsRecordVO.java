package com.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsRecordVO {
    private String regionName;
    private String businessName;
    private String counterName;
    private String operatorName;
    private String ticketStatus;
    private String customerName;
    private String ticketNo;
    private LocalDateTime createdAt;
    private LocalDateTime calledAt;
    private LocalDateTime servedAt;
    private LocalDateTime completedAt;
    private Long durationSeconds;
}
