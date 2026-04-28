package com.queue.util;

public class PhoneUtil {
    private PhoneUtil() {
    }

    public static String normalize(String phone) {
        if (phone == null) {
            return null;
        }
        String trimmed = phone.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        String digits = trimmed.replaceAll("\\D", "");
        return digits.isEmpty() ? trimmed : digits;
    }

    public static String mask(String phone) {
        String normalized = normalize(phone);
        if (normalized == null || normalized.length() < 7) {
            return normalized;
        }
        return normalized.substring(0, 3) + "****" + normalized.substring(normalized.length() - 4);
    }

    public static String extractLast4(String phone) {
        String normalized = normalize(phone);
        if (normalized == null || normalized.isEmpty()) {
            return normalized;
        }
        return normalized.length() <= 4 ? normalized : normalized.substring(normalized.length() - 4);
    }

    public static boolean looksLikeCompletePhone(String phone) {
        if (phone == null || phone.contains("*")) {
            return false;
        }
        String normalized = normalize(phone);
        return normalized != null && normalized.matches("\\d{7,20}");
    }
}
