package com.queue.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.queue.common.Result;
import com.queue.entity.QrCodeRecord;
import com.queue.entity.Region;
import com.queue.entity.SysUser;
import com.queue.mapper.SysUserMapper;
import com.queue.service.QrCodeRecordService;
import com.queue.config.ServerConfig;
import com.queue.service.RegionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/qrcode")
@RequiredArgsConstructor
public class QrCodeController {
    private final RegionService regionService;
    private final QrCodeRecordService qrCodeRecordService;
    private final SysUserMapper sysUserMapper;
    private final ServerConfig serverConfig;

    @GetMapping(value = "/generate", produces = MediaType.IMAGE_PNG_VALUE)
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

        String url = effectiveBaseUrl + "/appointment?region=" + region.getRegionCode();

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

    @GetMapping("/url")
    public Result<Map<String, Object>> getUrl(
            @RequestParam String regionCode,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String baseUrl,
            HttpServletRequest request) {
        String effectiveBaseUrl = resolveFrontendBaseUrl(baseUrl, request);
        Region region = regionService.getByCode(regionCode);
        if (region == null) {
            return Result.error(400, "区域不存在");
        }
        // 权限校验
        String createdBy = null;
        if (userId != null && userId > 0) {
            SysUser user = sysUserMapper.selectById(userId);
            if (user != null) {
                createdBy = user.getName();
                if (!"SUPER_ADMIN".equals(user.getRole())) {
                    List<Long> scopedRoots = sysUserMapper.selectRegionScopeIds(userId);
                    if (scopedRoots != null && !scopedRoots.isEmpty()) {
                        Set<Long> allowedSet = new HashSet<>();
                        for (Long rid : scopedRoots) {
                            if (rid == null) continue;
                            allowedSet.addAll(regionService.getDescendantRegionIds(rid));
                        }
                        if (!allowedSet.contains(region.getId())) {
                            return Result.error(403, "无权操作该区域");
                        }
                        String url = effectiveBaseUrl + "/appointment?region=" + region.getRegionCode();
                        QrCodeRecord record = qrCodeRecordService.saveOrUpdate(region.getId(), region.getRegionCode(), region.getRegionName(), url, createdBy);

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

                    if (user.getRegionCode() == null || user.getRegionCode().isEmpty()) {
                        return Result.error(403, "无权操作该区域");
                    }
                    Region userRegion = regionService.getByCode(user.getRegionCode());
                    if (userRegion == null) {
                        return Result.error(403, "无权操作该区域");
                    }
                    List<Long> allowedRegionIds = regionService.getDescendantRegionIds(userRegion.getId());
                    if (!allowedRegionIds.contains(region.getId())) {
                        return Result.error(403, "无权操作该区域");
                    }
                }
            }
        }
        String url = effectiveBaseUrl + "/appointment?region=" + region.getRegionCode();
        QrCodeRecord record = qrCodeRecordService.saveOrUpdate(region.getId(), region.getRegionCode(), region.getRegionName(), url, createdBy);

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

    @GetMapping("/list")
    public Result<List<QrCodeRecord>> list(@RequestParam(required = false) Long userId) {
        List<QrCodeRecord> allRecords = qrCodeRecordService.listAll();
        if (userId == null || userId <= 0) {
            return Result.ok(allRecords);
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || "SUPER_ADMIN".equals(user.getRole())) {
            return Result.ok(allRecords);
        }
        List<Long> scopedRoots = sysUserMapper.selectRegionScopeIds(userId);
        Set<Long> allowedSet = new HashSet<>();
        if (scopedRoots != null && !scopedRoots.isEmpty()) {
            for (Long rid : scopedRoots) {
                if (rid == null) continue;
                allowedSet.addAll(regionService.getDescendantRegionIds(rid));
            }
        } else {
            if (user.getRegionCode() == null || user.getRegionCode().isEmpty()) {
                return Result.ok(List.of());
            }
            Region userRegion = regionService.getByCode(user.getRegionCode());
            if (userRegion == null) {
                return Result.ok(List.of());
            }
            List<Long> allowedRegionIds = regionService.getDescendantRegionIds(userRegion.getId());
            allowedSet.addAll(allowedRegionIds);
        }
        if (allowedSet.isEmpty()) {
            return Result.ok(List.of());
        }
        return Result.ok(allRecords.stream()
            .filter(r -> allowedSet.contains(r.getRegionId()))
            .collect(Collectors.toList()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        qrCodeRecordService.delete(id);
        return Result.ok();
    }

    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        qrCodeRecordService.deleteByIds(ids);
        return Result.ok();
    }
}
