package com.queue.enums;

import lombok.Getter;

@Getter
public enum TicketSource {
    ONLINE("online"),
    APPOINTMENT("appointment"),
    MANUAL("manual");

    private final String value;

    TicketSource(String value) {
        this.value = value;
    }

    public static TicketSource fromValue(String value) {
        for (TicketSource source : TicketSource.values()) {
            if (source.value.equals(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown TicketSource value: " + value);
    }
}
