package com.queue.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScreenWaitingTicketRow {
    private Long id;
    private String ticketNo;
    private String businessTypeName;
    private Boolean reactivated;
    private LocalDateTime createdAt;
}
