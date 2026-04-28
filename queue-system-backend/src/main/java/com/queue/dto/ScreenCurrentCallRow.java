package com.queue.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScreenCurrentCallRow {
    private String ticketNo;
    private Long counterId;
    private String counterName;
    private LocalDateTime calledAt;
    private String businessTypeName;
}
