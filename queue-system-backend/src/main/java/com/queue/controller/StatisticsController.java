package com.queue.controller;

import com.queue.common.BusinessException;
import com.queue.common.Result;
import com.queue.common.ResultCode;
import com.queue.dto.StatisticsRequest;
import com.queue.dto.StatisticsResponse;
import com.queue.entity.SysUser;
import com.queue.mapper.SysUserMapper;
import com.queue.service.RegionService;
import com.queue.service.StatisticsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final SysUserMapper sysUserMapper;
    private final RegionService regionService;

    @GetMapping("/list")
    public Result<StatisticsResponse> list(StatisticsRequest request, HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        // 非超级管理员需要过滤可见区域
        if (userId != null) {
            SysUser user = sysUserMapper.selectById(userId);
            if (user != null && !"SUPER_ADMIN".equals(user.getRole())) {
                // 获取用户可见的区域ID列表
                List<Long> allowedRegionIds = getAllowedRegionIds(user);
                if (allowedRegionIds.isEmpty()) {
                    // 无可见区域，返回空结果
                    StatisticsResponse emptyResponse = new StatisticsResponse();
                    emptyResponse.setRecords(List.of());
                    emptyResponse.setTotal(0L);
                    emptyResponse.setPageNum(request.getPageNum() != null ? request.getPageNum() : 1);
                    emptyResponse.setPageSize(request.getPageSize() != null ? request.getPageSize() : 10);
                    emptyResponse.setTotalPages(0);
                    return Result.ok(emptyResponse);
                }
                request.setAllowedRegionIds(allowedRegionIds);
            }
        }
        StatisticsResponse response = statisticsService.queryStatistics(request);
        return Result.ok(response);
    }

    @GetMapping("/export")
    public void export(StatisticsRequest request, HttpServletResponse response, HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        // 非超级管理员需要过滤可见区域
        if (userId != null) {
            SysUser user = sysUserMapper.selectById(userId);
            if (user != null && !"SUPER_ADMIN".equals(user.getRole())) {
                List<Long> allowedRegionIds = getAllowedRegionIds(user);
                if (allowedRegionIds.isEmpty()) {
                    throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "无导出权限");
                }
                request.setAllowedRegionIds(allowedRegionIds);
            }
        }
        statisticsService.exportStatistics(request, response);
    }

    /**
     * 获取用户可见的区域ID列表（包含自身及子区域）
     */
    private List<Long> getAllowedRegionIds(SysUser user) {
        List<Long> scopedRoots = sysUserMapper.selectRegionScopeIds(user.getId());
        if (scopedRoots != null && !scopedRoots.isEmpty()) {
            Set<Long> all = new HashSet<>();
            for (Long rid : scopedRoots) {
                if (rid == null) continue;
                all.addAll(regionService.getDescendantRegionIds(rid));
            }
            return new ArrayList<>(all);
        }

        if (user.getRegionCode() == null || user.getRegionCode().isEmpty()) {
            return List.of();
        }
        var userRegion = regionService.getByCode(user.getRegionCode());
        if (userRegion == null) {
            return List.of();
        }
        return regionService.getDescendantRegionIds(userRegion.getId());
    }
}
