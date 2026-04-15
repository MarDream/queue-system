package com.queue.enums;

import lombok.Getter;

@Getter
public enum CounterStatus {
    IDLE("idle"),
    BUSY("busy"),
    PAUSED("paused");

    private final String value;

    CounterStatus(String value) {
        this.value = value;
    }

    public static CounterStatus fromValue(String value) {
        for (CounterStatus status : CounterStatus.values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown CounterStatus value: " + value);
    }
}
