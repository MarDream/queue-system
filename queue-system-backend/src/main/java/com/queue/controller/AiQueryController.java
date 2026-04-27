package com.queue.controller;

import com.queue.common.Result;
import com.queue.dto.AiAskRequest;
import com.queue.dto.AiAskResponse;
import com.queue.dto.AiMessageDTO;
import com.queue.dto.AiSessionDTO;
import com.queue.service.AiQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ai")
@RequiredArgsConstructor
public class AiQueryController {

    private final AiQueryService aiQueryService;

    @PostMapping("/ask")
    public Result<AiAskResponse> ask(@RequestBody AiAskRequest request, HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        return Result.ok(aiQueryService.ask(userId, request));
    }

    @GetMapping("/sessions")
    public Result<List<AiSessionDTO>> sessions(@RequestParam(defaultValue = "admin") String workspace,
                                               HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        return Result.ok(aiQueryService.listSessions(userId, workspace));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<AiMessageDTO>> messages(@PathVariable String sessionId,
                                               @RequestParam(defaultValue = "admin") String workspace,
                                               HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        return Result.ok(aiQueryService.getMessages(userId, workspace, sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> delete(@PathVariable String sessionId,
                               @RequestParam(defaultValue = "admin") String workspace,
                               HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        aiQueryService.deleteSession(userId, workspace, sessionId);
        return Result.ok(null);
    }

    @GetMapping("/export")
    public void export(@RequestParam String sessionId,
                       @RequestParam(defaultValue = "admin") String workspace,
                       HttpServletRequest req,
                       HttpServletResponse response) throws Exception {
        Long userId = (Long) req.getAttribute("userId");
        Map<String, Object> last = aiQueryService.getLastTable(userId, workspace, sessionId);
        if (last == null) {
            response.sendError(404, "暂无可导出的结果");
            return;
        }

        Object colsObj = last.get("columns");
        Object rowsObj = last.get("rows");
        if (!(colsObj instanceof List) || !(rowsObj instanceof List)) {
            response.sendError(404, "暂无可导出的结果");
            return;
        }

        List<?> cols = (List<?>) colsObj;
        List<?> rows = (List<?>) rowsObj;

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("智能问数");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < cols.size(); i++) {
                Object c = cols.get(i);
                String label = "";
                if (c instanceof Map) {
                    Object v = ((Map<?, ?>) c).get("label");
                    label = v == null ? "" : String.valueOf(v);
                } else {
                    try {
                        var m = c.getClass().getMethod("getLabel");
                        Object v = m.invoke(c);
                        label = v == null ? "" : String.valueOf(v);
                    } catch (Exception ignored) {
                    }
                }
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(label);
            }

            for (int r = 0; r < rows.size(); r++) {
                Object rowObj = rows.get(r);
                if (!(rowObj instanceof Map)) {
                    continue;
                }
                Map<?, ?> rowMap = (Map<?, ?>) rowObj;
                Row row = sheet.createRow(r + 1);
                for (int i = 0; i < cols.size(); i++) {
                    Object c = cols.get(i);
                    String key = null;
                    if (c instanceof Map) {
                        Object v = ((Map<?, ?>) c).get("key");
                        key = v == null ? null : String.valueOf(v);
                    } else {
                        try {
                            var m = c.getClass().getMethod("getKey");
                            Object v = m.invoke(c);
                            key = v == null ? null : String.valueOf(v);
                        } catch (Exception ignored) {
                        }
                    }
                    Object v = key == null ? null : rowMap.get(key);
                    row.createCell(i).setCellValue(v == null ? "" : String.valueOf(v));
                }
            }

            for (int i = 0; i < cols.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            String filename = "ai_export_" + System.currentTimeMillis() + ".xlsx";
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
            workbook.write(response.getOutputStream());
        }
    }
}
