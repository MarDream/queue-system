package com.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsResponse {
    private List<StatisticsRecordVO> records;
    private Long total;
    private Integer pageNum;
    private Integer pageSize;
    private Integer totalPages;
}
