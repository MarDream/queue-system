package com.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScreenDataResponse {
    private List<CurrentCallVO> currentCalls;
    private List<CounterStatusVO> counters;
    private List<RecentCallVO> recentCalls;
    private List<WaitingTicketVO> waitingQueue;
    private ScreenStatsVO stats;
    private String announcementText;
    private String regionName;
    private Long regionId;
    private String regionCode;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentCallVO {
        private String ticketNo;
        private String counterId;
        private String counterName;
        private String calledAt;
        private String businessTypeName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CounterStatusVO {
        private String name;
        private String status;
        private String currentTicketNo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentCallVO {
        private String ticketNo;
        private String counterName;
        private String calledAt;
        private String businessTypeName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaitingTicketVO {
        private Long id;
        private String ticketNo;
        private String businessTypeName;
        private Boolean reactivated;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScreenStatsVO {
        private Integer totalToday;
        private Integer waitingCount;
        private Integer completedCount;
        private Integer skippedCount;
    }
}
