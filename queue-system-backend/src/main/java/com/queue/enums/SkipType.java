package com.queue.enums;

import lombok.Getter;

@Getter
public enum SkipType {
    MANUAL("manual", "人工跳过"),
    SYSTEM("system", "系统过号");

    private final String value;
    private final String label;

    SkipType(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public static SkipType fromValue(String value) {
        for (SkipType type : SkipType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
