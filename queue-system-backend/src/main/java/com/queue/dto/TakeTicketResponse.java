package com.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TakeTicketResponse {
    private String ticketNo;
    private String businessType;
    private Integer waitingCount;
    private Integer estimatedWaitMinutes;
    private LocalDateTime createdAt;
}
