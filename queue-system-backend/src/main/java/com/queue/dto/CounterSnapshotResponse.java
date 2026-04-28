package com.queue.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CounterSnapshotResponse {
    private Long counterId;
    private String counterName;
    private String currentStatus;
    private Boolean paused;
    private String currentTicketNo;
    private String currentTicketStatus;
    private String currentBusinessTypeName;
    private Integer todayCompletedCount;
    private Integer waitingCount;
    private List<WaitingTicketVO> waitingQueue;

    @Data
    public static class WaitingTicketVO {
        private Long id;
        private String ticketNo;
        private String businessTypeName;
        private Boolean reactivated;
        private LocalDateTime createdAt;
    }
}
