package com.queue.enums;

import lombok.Getter;

@Getter
public enum TimeSlot {
    MORNING("morning"),
    AFTERNOON("afternoon");

    private final String value;

    TimeSlot(String value) {
        this.value = value;
    }

    public static TimeSlot fromValue(String value) {
        for (TimeSlot slot : TimeSlot.values()) {
            if (slot.value.equals(value)) {
                return slot;
            }
        }
        throw new IllegalArgumentException("Unknown TimeSlot value: " + value);
    }
}
