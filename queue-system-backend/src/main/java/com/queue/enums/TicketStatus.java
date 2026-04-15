package com.queue.enums;

import lombok.Getter;

@Getter
public enum TicketStatus {
    WAITING("waiting", "等待中"),
    CALLED("called", "已叫号"),
    SERVING("serving", "办理中"),
    COMPLETED("completed", "已办结"),
    SKIPPED("skipped", "已过号"),
    CANCELLED("cancelled", "已取消");

    private final String value;
    private final String label;

    TicketStatus(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public static TicketStatus fromValue(String value) {
        for (TicketStatus status : TicketStatus.values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TicketStatus value: " + value);
    }
}
