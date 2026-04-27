package com.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminTicketVO {
    private Long id;
    private String ticketNo;
    private String businessType;
    private String status;
    private String statusText;
    private String phone;
    private String name;
    private String regionName;
    private String counterName;
    private LocalDateTime createdAt;
    private LocalDateTime calledAt;
    private LocalDateTime servedAt;
    private LocalDateTime completedAt;
    private String skipType; // 过号来源类型：manual=人工跳过，system=系统过号
    private Boolean reactivated;
}
