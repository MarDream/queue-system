package com.queue.service;

import com.queue.dto.StatisticsRequest;
import com.queue.dto.StatisticsRecordVO;
import com.queue.dto.StatisticsResponse;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

public interface StatisticsService {
    StatisticsResponse queryStatistics(StatisticsRequest request);
    void exportStatistics(StatisticsRequest request, HttpServletResponse response);
}
