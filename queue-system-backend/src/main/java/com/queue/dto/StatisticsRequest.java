package com.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsRequest {
    private Long regionId;
    private Long businessTypeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private List<String> fields;
}
