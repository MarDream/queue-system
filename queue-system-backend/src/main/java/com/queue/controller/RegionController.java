package com.queue.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.queue.common.Result;
import com.queue.common.ResultCode;
import com.queue.dto.RegionPageRequest;
import com.queue.dto.RegionSortRequest;
import com.queue.entity.Region;
import com.queue.entity.SysUser;
import com.queue.mapper.SysUserMapper;
import com.queue.service.RegionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
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

    @GetMapping
    public Result<List<Region>> listAll(@RequestParam(required = false) Long userId) {
        List<Region> regions = regionService.listAll();
        List<Long> allowed = getAllowedRegionIds(userId);
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
    public Result<IPage<Region>> listPage(RegionPageRequest request,
            @RequestParam(required = false) Long userId) {
        List<Long> allowed = getAllowedRegionIds(userId);
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
    public Result<List<Region>> listCities(@RequestParam(required = false) Long userId) {
        List<Region> allCities = regionService.listByLevel("city");
        if (userId == null || userId <= 0) {
            return Result.ok(allCities);
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || "SUPER_ADMIN".equals(user.getRole())) {
            return Result.ok(allCities);
        }
        if (user.getRegionCode() == null || user.getRegionCode().isEmpty()) {
            return Result.ok(List.of());
        }
        Region userRegion = regionService.getByCode(user.getRegionCode());
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
    public Result<List<Region>> listTowns(@PathVariable Long parentId,
            @RequestParam(required = false) Long userId) {
        // 校验 parentId 在用户管辖范围内
        List<Long> allowed = getAllowedRegionIds(userId);
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
    public Result<Region> create(@RequestBody Region region,
            @RequestParam(required = false) Long userId,
            Authentication authentication) {
        if ("city".equals(region.getLevel())) {
            requireSuperAdmin(authentication);
        } else {
            // 非城市级：校验 parentId 在用户管辖范围内
            requireParentRegionAccess(userId, region.getParentId(), authentication);
        }
        return Result.ok(regionService.create(region));
    }

    @PutMapping("/{id}")
    public Result<Region> update(@PathVariable Long id, @RequestBody Region region,
            @RequestParam(required = false) Long userId,
            Authentication authentication) {
        Region existing = regionService.getById(id);
        if (existing != null && "city".equals(existing.getLevel())) {
            requireSuperAdmin(authentication);
        } else {
            // 非城市级：校验用户有权操作此区域
            requireRegionAccess(userId, id, authentication);
        }
        region.setId(id);
        return Result.ok(regionService.update(region));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id,
            @RequestParam(required = false) Long userId,
            Authentication authentication) {
        Region existing = regionService.getById(id);
        if (existing != null && "city".equals(existing.getLevel())) {
            requireSuperAdmin(authentication);
        } else {
            requireRegionAccess(userId, id, authentication);
        }

        // 区域管理员不能删除自己直接所属的区域
        if (userId != null && userId > 0) {
            SysUser user = sysUserMapper.selectById(userId);
            if (user != null && "REGION_ADMIN".equals(user.getRole())) {
                Region userRegion = regionService.getByCode(user.getRegionCode());
                if (userRegion != null && userRegion.getId().equals(id)) {
                    throw new com.queue.common.BusinessException(40003, "不能删除自己所属的区域");
                }
            }
        }

        regionService.delete(id);
        return Result.ok();
    }

    @PostMapping("/batch-sort")
    public Result<Void> batchUpdateSort(@RequestBody List<RegionSortRequest> requests) {
        regionService.batchUpdateSort(requests);
        return Result.ok();
    }

    /**
     * Check if the current user is a SUPER_ADMIN.
     * Throws BusinessException if not.
     */
    private void requireSuperAdmin(Authentication authentication) {
        if (authentication == null) {
            throw new com.queue.common.BusinessException(ResultCode.UNAUTHORIZED);
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        boolean isSuperAdmin = authorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        if (!isSuperAdmin) {
            throw new com.queue.common.BusinessException(40003, "无权限操作市级区域");
        }
    }

    /**
     * Get allowed region IDs for a user.
     * Returns null if no filtering needed (userId is null/0 or user is SUPER_ADMIN).
     * Returns empty list if user has no region assigned.
     */
    private List<Long> getAllowedRegionIds(Long userId) {
        if (userId == null || userId <= 0) {
            return null; // 不过滤
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || "SUPER_ADMIN".equals(user.getRole())) {
            return null; // 不过滤
        }
        if (user.getRegionCode() == null || user.getRegionCode().isEmpty()) {
            return Collections.emptyList();
        }
        Region userRegion = regionService.getByCode(user.getRegionCode());
        if (userRegion == null) {
            return Collections.emptyList();
        }
        return regionService.getDescendantRegionIds(userRegion.getId());
    }

    /**
     * Check if user has access to a specific region.
     */
    private void requireRegionAccess(Long userId, Long regionId, Authentication authentication) {
        if (userId == null || userId <= 0) {
            return;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || "SUPER_ADMIN".equals(user.getRole())) {
            return;
        }
        List<Long> allowed = getAllowedRegionIds(userId);
        if (allowed == null || !allowed.contains(regionId)) {
            throw new com.queue.common.BusinessException(40003, "无权限操作该区域");
        }
    }

    /**
     * Check if user has access to create a region under a specific parent.
     */
    private void requireParentRegionAccess(Long userId, Long parentId, Authentication authentication) {
        if (parentId == null) {
            // 创建城市级 - 已有 requireSuperAdmin 处理
            return;
        }
        requireRegionAccess(userId, parentId, authentication);
    }
}
