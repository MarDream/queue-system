package com.queue.service.impl;

import com.queue.dto.StatisticsRequest;
import com.queue.dto.StatisticsRecordVO;
import com.queue.dto.StatisticsResponse;
import com.queue.mapper.StatisticsMapper;
import com.queue.service.StatisticsService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class StatisticsServiceImpl implements StatisticsService {

    private final StatisticsMapper statisticsMapper;
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public StatisticsServiceImpl(StatisticsMapper statisticsMapper) {
        this.statisticsMapper = statisticsMapper;
    }

    @Override
    public StatisticsResponse queryStatistics(StatisticsRequest request) {
        normalizeDateRange(request);

        int pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
        long total = statisticsMapper.countStatistics(request);
        if (total == 0) {
            StatisticsResponse response = new StatisticsResponse();
            response.setRecords(List.of());
            response.setTotal(0L);
            response.setPageNum(pageNum);
            response.setPageSize(pageSize);
            response.setTotalPages(0);
            return response;
        }

        long offset = (long) Math.max(pageNum - 1, 0) * pageSize;
        List<StatisticsRecordVO> pagedRecords = statisticsMapper.selectStatisticsPage(request, offset, pageSize);

        StatisticsResponse response = new StatisticsResponse();
        response.setRecords(pagedRecords);
        response.setTotal(total);
        response.setPageNum(pageNum);
        response.setPageSize(pageSize);
        response.setTotalPages((int) Math.ceil((double) total / pageSize));
        return response;
    }

    @Override
    public void exportStatistics(StatisticsRequest request, HttpServletResponse response) {
        normalizeDateRange(request);
        List<StatisticsRecordVO> records = statisticsMapper.selectStatisticsList(request);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("统计分析");

            String[] headers = {"区域名称", "业务名称", "窗口名称", "操作人员", "状态", "过号来源", "客户姓名", "票号",
                               "取号时间", "叫号时间", "开始服务时间", "完成时间", "办理时长(秒)"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            int rowNum = 1;
            for (StatisticsRecordVO record : records) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(record.getRegionName());
                row.createCell(1).setCellValue(record.getBusinessName());
                row.createCell(2).setCellValue(record.getCounterName());
                row.createCell(3).setCellValue(record.getOperatorName());
                row.createCell(4).setCellValue(record.getTicketStatus());
                row.createCell(5).setCellValue("manual".equals(record.getSkipType()) ? "人工跳过" : "system".equals(record.getSkipType()) ? "系统过号" : "");
                row.createCell(6).setCellValue(record.getCustomerName());
                row.createCell(7).setCellValue(record.getTicketNo());
                row.createCell(8).setCellValue(record.getCreatedAt() != null ? record.getCreatedAt().format(DT_FORMATTER) : "");
                row.createCell(9).setCellValue(record.getCalledAt() != null ? record.getCalledAt().format(DT_FORMATTER) : "");
                row.createCell(10).setCellValue(record.getServedAt() != null ? record.getServedAt().format(DT_FORMATTER) : "");
                row.createCell(11).setCellValue(record.getCompletedAt() != null ? record.getCompletedAt().format(DT_FORMATTER) : "");
                row.createCell(12).setCellValue(record.getDurationSeconds() != null ? record.getDurationSeconds().toString() : "");
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            String filename = "statistics_" + System.currentTimeMillis() + ".xlsx";
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
            workbook.write(response.getOutputStream());
        } catch (IOException e) {
            throw new RuntimeException("Export failed", e);
        }
    }

    private void normalizeDateRange(StatisticsRequest request) {
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();

        request.setCreatedAtStart(startDate == null ? null : startDate.atStartOfDay());
        request.setCreatedAtEndExclusive(endDate == null ? null : endDate.plusDays(1).atStartOfDay());
    }
}
