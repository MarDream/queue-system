package com.queue.dto;

import lombok.Data;

/**
 * Response for checking user's active ticket in a specific region.
 * Returns the first unfinished ticket (WAITING/CALLED/SERVING) if exists.
 */
@Data
public class ActiveTicketResponse {
    // Has active ticket
    private Boolean hasActive;

    // Ticket info (only when hasActive = true)
    private String ticketNo;
    private String businessTypeName;
    private String status;          // WAITING / CALLED / SERVING
    private String statusText;      // 等待中 / 已叫号 / 服务中
    private String counterName;
    private Integer waitingCount;   // 前方等待人数
    private Integer estimatedWaitMinutes;
    private String createdAt;

    // For returning to take ticket form
    private Long regionId;
    private String regionName;
}
