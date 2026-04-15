package com.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueueStatusResponse {
    private String ticketNo;
    private String status;
    private String businessType;
    private Integer waitingCount;
    private Integer estimatedWaitMinutes;
    private String counterName;
}
