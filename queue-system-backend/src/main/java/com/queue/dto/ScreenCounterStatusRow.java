package com.queue.dto;

import lombok.Data;

@Data
public class ScreenCounterStatusRow {
    private Long counterId;
    private String name;
    private String status;
    private String currentTicketNo;
}
