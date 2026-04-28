package com.queue.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.queue.common.Result;
import com.queue.common.ResultCode;
import com.queue.dto.RegionPageRequest;
import com.queue.dto.RegionSortRequest;
import com.queue.entity.Region;
import com.queue.entity.SysUser;
import com.queue.mapper.SysUserMapper;
import com.queue.service.AuthContextService;
import com.queue.service.RegionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController {
    private final RegionService regionService;
    private final SysUserMapper sysUserMapper;
    private final AuthContextService authContextService;

    @GetMapping
    public Result<List<Region>> listAll(HttpServletRequest request) {
        List<Region> regions = regionService.listAll();
        List<Long> allowed = getAllowedRegionIds(authContextService.getCurrentUser(request));
        if (allowed == null) {
            return Result.ok(regions); // 不过滤
        }
        if (allowed.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        Set<Long> allowedSet = new HashSet<>(allowed);
        return Result.ok(regions.stream()
                .filter(r -> allowedSet.contains(r.getId()))
                .collect(Collectors.toList()));
    }

    @GetMapping("/list")
    public Result<IPage<Region>> listPage(RegionPageRequest request, HttpServletRequest httpRequest) {
        List<Long> allowed = getAllowedRegionIds(authContextService.getCurrentUser(httpRequest));
        if (allowed != null && allowed.isEmpty()) {
            // 有权限限制但无区域 -> 返回空页
            return Result.ok(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(request.getPageNum() != null ? request.getPageNum() : 1, request.getPageSize() != null ? request.getPageSize() : 10));
        }
        if (allowed != null && !allowed.isEmpty()) {
            // 非超级管理员：从全量过滤后手动分页
            List<Region> allFiltered = regionService.listAll().stream()
                    .filter(r -> allowed.contains(r.getId()))
                    .collect(Collectors.toList());
            int pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
            int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
            int start = (pageNum - 1) * pageSize;
            int end = Math.min(start + pageSize, allFiltered.size());
            List<Region> paged = start < allFiltered.size() ? allFiltered.subList(start, end) : Collections.emptyList();
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Region> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>((long) pageNum, (long) pageSize, allFiltered.size());
            page.setRecords(paged);
            return Result.ok(page);
        }
        return Result.ok(regionService.listPage(request));
    }

    @GetMapping("/cities")
    public Result<List<Region>> listCities(HttpServletRequest request) {
        List<Region> allCities = regionService.listByLevel("city");
        SysUser currentUser = authContextService.getCurrentUser(request);
        if (currentUser == null) {
            return Result.ok(allCities);
        }
        if (authContextService.isSuperAdmin(currentUser)) {
            return Result.ok(allCities);
        }
        if (currentUser.getRegionCode() == null || currentUser.getRegionCode().isEmpty()) {
            return Result.ok(List.of());
        }
        Region userRegion = regionService.getByCode(currentUser.getRegionCode());
        Region city = userRegion;
        while (city != null && !"city".equals(city.getLevel())) {
            city = city.getParentId() != null ? regionService.getById(city.getParentId()) : null;
        }
        if (city == null) {
            return Result.ok(List.of());
        }
        // 如果用户本身不是 city 级别（是 town 或 street），只能返回其所属 city
        if (!"city".equals(userRegion.getLevel())) {
            return Result.ok(List.of(city));
        }
        // 用户自己是 city 级别，返回同级城市
        final Long cityParentId = city.getParentId();
        List<Region> siblingCities;
        if (cityParentId == null) {
            siblingCities = allCities;
        } else {
            siblingCities = allCities.stream()
                .filter(c -> c.getParentId() == null || c.getParentId().equals(cityParentId))
                .collect(Collectors.toList());
        }
        return Result.ok(siblingCities);
    }

    @GetMapping("/{parentId}/towns")
    public Result<List<Region>> listTowns(@PathVariable Long parentId, HttpServletRequest request) {
        // 校验 parentId 在用户管辖范围内
        List<Long> allowed = getAllowedRegionIds(authContextService.getCurrentUser(request));
        if (allowed != null && !allowed.isEmpty() && !allowed.contains(parentId)) {
            return Result.ok(Collections.emptyList()); // 无权访问该 parentId
        }
        return Result.ok(regionService.listByParentId(parentId));
    }

    @GetMapping("/{id}")
    public Result<Region> getById(@PathVariable Long id) {
        return Result.ok(regionService.getById(id));
    }

    @GetMapping("/code/{code}")
    public Result<Region> getByCode(@PathVariable String code) {
        return Result.ok(regionService.getByCode(code));
    }

    @GetMapping("/code/{code}/fullname")
    public Result<String> getFullRegionName(@PathVariable String code) {
        return Result.ok(regionService.getFullRegionName(code));
    }

    @PostMapping
    public Result<Region> create(@RequestBody Region region, HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        if ("city".equals(region.getLevel())) {
            requireSuperAdmin(currentUser);
        } else {
            // 非城市级：校验 parentId 在用户管辖范围内
            requireParentRegionAccess(currentUser, region.getParentId());
        }
        return Result.ok(regionService.create(region));
    }

    @PutMapping("/{id}")
    public Result<Region> update(@PathVariable Long id, @RequestBody Region region, HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        Region existing = regionService.getById(id);
        if (existing != null && "city".equals(existing.getLevel())) {
            requireSuperAdmin(currentUser);
        } else {
            // 非城市级：校验用户有权操作此区域
            requireRegionAccess(currentUser, id);
            if (region.getParentId() != null) {
                requireRegionAccess(currentUser, region.getParentId());
            }
        }
        region.setId(id);
        return Result.ok(regionService.update(region));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        Region existing = regionService.getById(id);
        if (existing != null && "city".equals(existing.getLevel())) {
            requireSuperAdmin(currentUser);
        } else {
            requireRegionAccess(currentUser, id);
        }

        // 区域管理员不能删除自己直接所属的区域
        if ("REGION_ADMIN".equals(currentUser.getRole())) {
            Region userRegion = regionService.getByCode(currentUser.getRegionCode());
            if (userRegion != null && userRegion.getId().equals(id)) {
                throw new com.queue.common.BusinessException(40003, "不能删除自己所属的区域");
            }
        }

        regionService.delete(id);
        return Result.ok();
    }

    @PostMapping("/batch-sort")
    public Result<Void> batchUpdateSort(@RequestBody List<RegionSortRequest> requests, HttpServletRequest request) {
        SysUser currentUser = authContextService.requireCurrentUser(request);
        if (!authContextService.isSuperAdmin(currentUser)) {
            List<Long> allowed = getAllowedRegionIds(currentUser);
            Set<Long> allowedSet = allowed == null ? Collections.emptySet() : new HashSet<>(allowed);
            boolean hasForbidden = requests.stream()
                    .map(RegionSortRequest::getId)
                    .anyMatch(regionId -> regionId == null || !allowedSet.contains(regionId));
            if (hasForbidden) {
                throw new com.queue.common.BusinessException(40003, "包含无权限操作的区域");
            }
        }
        regionService.batchUpdateSort(requests);
        return Result.ok();
    }

    /**
     * Check if the current user is a SUPER_ADMIN.
     * Throws BusinessException if not.
     */
    private void requireSuperAdmin(SysUser currentUser) {
        if (!authContextService.isSuperAdmin(currentUser)) {
            throw new com.queue.common.BusinessException(40003, "无权限操作市级区域");
        }
    }

    /**
     * Get allowed region IDs for a user.
     * Returns null if no filtering needed (public or SUPER_ADMIN).
     * Returns empty list if user has no region assigned.
     */
    private List<Long> getAllowedRegionIds(SysUser user) {
        if (user == null) {
            return null; // 不过滤
        }
        Set<Long> allowed = authContextService.resolveAllowedRegionIds(user);
        if (allowed == null) {
            return null;
        }
        return List.copyOf(allowed);
    }

    /**
     * Check if user has access to a specific region.
     */
    private void requireRegionAccess(SysUser user, Long regionId) {
        authContextService.assertRegionAccess(user, regionId);
    }

    /**
     * Check if user has access to create a region under a specific parent.
     */
    private void requireParentRegionAccess(SysUser user, Long parentId) {
        if (parentId == null) {
            // 创建城市级 - 已有 requireSuperAdmin 处理
            return;
        }
        requireRegionAccess(user, parentId);
    }
}
