package com.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private Integer totalTickets;
    private Integer completedCount;
    private Integer waitingCount;
    private Double avgWaitMinutes;
    private Double avgServeMinutes;
    private List<CounterStatVO> counterStats;
    private List<BusinessTypeStatVO> businessTypeStats;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CounterStatVO {
        private String counterName;
        private Integer completedCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessTypeStatVO {
        private Long businessTypeId;
        private String businessType;
        private Integer count;
        private Double percentage;
    }
}
