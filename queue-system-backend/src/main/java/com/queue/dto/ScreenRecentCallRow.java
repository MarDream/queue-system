package com.queue.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScreenRecentCallRow {
    private String ticketNo;
    private String counterName;
    private LocalDateTime calledAt;
    private String businessTypeName;
}
