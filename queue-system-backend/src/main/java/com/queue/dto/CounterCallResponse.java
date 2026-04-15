package com.queue.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CounterCallResponse {
    private String ticketNo;
    private String businessType;
    private String customerName;
    private String counterName;
    private LocalDateTime calledAt;
}
