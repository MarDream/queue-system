package com.queue.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.queue.common.Result;
import com.queue.config.ServerConfig;
import com.queue.entity.QrCodeRecord;
import com.queue.entity.Region;
import com.queue.entity.SysUser;
import com.queue.mapper.QrCodeRecordMapper;
import com.queue.service.AuthContextService;
import com.queue.service.QrCodeRecordService;
import com.queue.service.RegionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class QrCodeController {
    private final RegionService regionService;
    private final QrCodeRecordService qrCodeRecordService;
    private final QrCodeRecordMapper qrCodeRecordMapper;
    private final AuthContextService authContextService;
    private final ServerConfig serverConfig;

    @GetMapping(value = "/api/v1/qrcode/generate", produces = MediaType.IMAGE_PNG_VALUE)
    public void generate(
            @RequestParam Long regionId,
            @RequestParam(required = false) String baseUrl,
            @RequestParam(defaultValue = "300") int size,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        String effectiveBaseUrl = resolveFrontendBaseUrl(baseUrl, request);

        Region region = regionService.getById(regionId);
        if (region == null) {
            response.sendError(404, "区域不存在");
            return;
        }

        String url = buildAppointmentUrl(effectiveBaseUrl, region.getRegionCode());

        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 2);

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(url, BarcodeFormat.QR_CODE, size, size, hints);

        response.setContentType(MediaType.IMAGE_PNG_VALUE);
        response.setHeader("Content-Disposition", "inline; filename=qrcode-" + region.getRegionCode() + ".png");

        OutputStream out = response.getOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);
        out.flush();
    }

    @GetMapping("/api/v1/admin/qrcode/url")
    public Result<Map<String, Object>> getUrl(
            @RequestParam String regionCode,
            @RequestParam(required = false) String baseUrl,
            HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        String effectiveBaseUrl = resolveFrontendBaseUrl(baseUrl, request);
        Region region = regionService.getByCode(regionCode);
        if (region == null) {
            return Result.error(400, "区域不存在");
        }

        authContextService.assertRegionAccess(currentUser, region.getId());

        String url = buildAppointmentUrl(effectiveBaseUrl, region.getRegionCode());
        QrCodeRecord record = qrCodeRecordService.saveOrUpdate(
                region.getId(),
                region.getRegionCode(),
                region.getRegionName(),
                url,
                currentUser.getName()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("id", record.getId());
        data.put("regionId", region.getId());
        data.put("regionCode", region.getRegionCode());
        data.put("regionName", region.getRegionName());
        data.put("url", url);
        data.put("createdAt", record.getCreatedAt());
        data.put("createdBy", record.getCreatedBy());
        return Result.ok(data);
    }

    @GetMapping("/api/v1/admin/qrcode/list")
    public Result<List<QrCodeRecord>> list(HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        List<QrCodeRecord> allRecords = qrCodeRecordService.listAll();
        Set<Long> allowedRegionIds = authContextService.resolveAllowedRegionIds(currentUser);
        if (allowedRegionIds == null) {
            return Result.ok(allRecords);
        }
        if (allowedRegionIds.isEmpty()) {
            return Result.ok(List.of());
        }
        return Result.ok(allRecords.stream()
                .filter(record -> allowedRegionIds.contains(record.getRegionId()))
                .toList());
    }

    @DeleteMapping("/api/v1/admin/qrcode/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        QrCodeRecord record = qrCodeRecordMapper.selectById(id);
        if (record == null) {
            return Result.ok();
        }
        authContextService.assertRegionAccess(currentUser, record.getRegionId());
        qrCodeRecordService.delete(id);
        return Result.ok();
    }

    @DeleteMapping("/api/v1/admin/qrcode/batch")
    public Result<Void> batchDelete(@RequestBody Map<String, List<Long>> body, HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        List<Long> ids = body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return Result.ok();
        }

        List<QrCodeRecord> records = qrCodeRecordMapper.selectBatchIds(ids);
        for (QrCodeRecord record : records) {
            if (record == null) {
                continue;
            }
            authContextService.assertRegionAccess(currentUser, record.getRegionId());
        }
        qrCodeRecordService.deleteByIds(ids);
        return Result.ok();
    }

    private String buildAppointmentUrl(String baseUrl, String regionCode) {
        return trimTrailingSlash(baseUrl) + "/appointment?region=" + regionCode;
    }

    private String resolveFrontendBaseUrl(String baseUrl, HttpServletRequest request) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return trimTrailingSlash(baseUrl);
        }

        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank() && !"null".equalsIgnoreCase(origin)) {
            return trimTrailingSlash(origin);
        }

        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            int pathIndex = referer.indexOf('/', referer.indexOf("://") + 3);
            String refererBase = pathIndex > 0 ? referer.substring(0, pathIndex) : referer;
            return trimTrailingSlash(refererBase);
        }

        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (forwardedProto != null && !forwardedProto.isBlank() && forwardedHost != null && !forwardedHost.isBlank()) {
            return trimTrailingSlash(forwardedProto + "://" + forwardedHost);
        }

        return trimTrailingSlash(serverConfig.getFrontendBaseUrl());
    }

    private String trimTrailingSlash(String url) {
        if (url == null) {
            return null;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
