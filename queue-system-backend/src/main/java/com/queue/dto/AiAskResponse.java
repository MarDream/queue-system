package com.queue.dto;

import lombok.Data;

import java.util.Map;
import java.util.List;

@Data
public class AiAskResponse {
    private String sessionId;
    private String answer;
    private List<AiCard> cards;
    private String queryType;
    private String intent;
    private List<String> capabilities;
    private List<AiTableColumn> tableColumns;
    private List<Map<String, Object>> tableRows;

    @Data
    public static class AiCard {
        private String title;
        private String value;
        private String unit;
    }

    @Data
    public static class AiTableColumn {
        private String key;
        private String label;
    }
}
