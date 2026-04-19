package com.queue.controller;

import com.queue.common.Result;
import com.queue.dto.StatisticsRequest;
import com.queue.dto.StatisticsResponse;
import com.queue.service.StatisticsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/list")
    public Result<StatisticsResponse> list(StatisticsRequest request) {
        StatisticsResponse response = statisticsService.queryStatistics(request);
        return Result.success(response);
    }

    @GetMapping("/export")
    public void export(StatisticsRequest request, HttpServletResponse response) {
        statisticsService.exportStatistics(request, response);
    }
}
