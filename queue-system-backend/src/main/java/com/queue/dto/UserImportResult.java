package com.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserImportResult {
    private Integer importedCount;
    private List<UserImportDetail> details;

    public UserImportResult(Integer importedCount) {
        this.importedCount = importedCount;
        this.details = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserImportDetail {
        private String username;
        private String generatedPassword;
    }
}
