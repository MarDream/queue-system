package com.queue.dto;

import lombok.Data;

import java.util.List;

@Data
public class CounterStatsVO {

    /** 今日已服务数（completed） */
    private int todayServedCount;

    /** 今日呼叫数（called + serving + completed） */
    private int todayCalledCount;

    /** 今日跳过数 */
    private int todaySkippedCount;

    /** 平均服务时长（分钟） */
    private double avgServiceMinutes;

    /** 平均等待时长（分钟） */
    private double avgWaitMinutes;

    /** 当前状态: idle/busy/paused */
    private String currentStatus;

    /** 当前正在服务的票号 */
    private String currentTicketNo;

    /** 当前业务类型名称 */
    private String currentBusinessTypeName;

    /** 该窗口支持的业务类型等待人数 */
    private List<BusinessWaitingInfo> waitingByBusiness;

    /** 最近服务记录 */
    private List<RecentServiceRecord> recentServices;

    @Data
    public static class BusinessWaitingInfo {
        private Long businessTypeId;
        private String businessTypeName;
        private String prefix;
        private int waitingCount;
    }

    @Data
    public static class RecentServiceRecord {
        private String ticketNo;
        private String businessTypeName;
        private String customerName;
        private String status;
        private String calledAt;
        private String completedAt;
        private double serviceMinutes;
    }
}
