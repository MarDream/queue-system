package com.queue.service;

import com.queue.common.BusinessException;
import com.queue.common.ResultCode;
import com.queue.entity.Region;
import com.queue.entity.SysUser;
import com.queue.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AuthContextService {

    private final SysUserMapper sysUserMapper;
    private final RegionService regionService;

    public AuthContextService(SysUserMapper sysUserMapper, @Lazy RegionService regionService) {
        this.sysUserMapper = sysUserMapper;
        this.regionService = regionService;
    }

    public Long getCurrentUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object userId = request.getAttribute("userId");
        if (userId instanceof Long value) {
            return value;
        }
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public SysUser getCurrentUser(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        return userId == null ? null : sysUserMapper.selectById(userId);
    }

    public SysUser requireCurrentUser(HttpServletRequest request) {
        SysUser user = getCurrentUser(request);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    public boolean isSuperAdmin(SysUser user) {
        return user != null && "SUPER_ADMIN".equals(user.getRole());
    }

    public Set<Long> resolveAllowedRegionIds(SysUser user) {
        if (user == null || isSuperAdmin(user)) {
            return null;
        }

        List<Long> scopedRoots = sysUserMapper.selectRegionScopeIds(user.getId());
        if (scopedRoots != null && !scopedRoots.isEmpty()) {
            Set<Long> all = new LinkedHashSet<>();
            for (Long regionId : scopedRoots) {
                if (regionId == null) {
                    continue;
                }
                all.addAll(regionService.getDescendantRegionIds(regionId));
            }
            return all;
        }

        Long rootId = user.getRegionId();
        if (rootId == null && user.getRegionCode() != null && !user.getRegionCode().isBlank()) {
            Region region = regionService.getByCode(user.getRegionCode());
            rootId = region == null ? null : region.getId();
        }
        if (rootId == null) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(regionService.getDescendantRegionIds(rootId));
    }

    public void assertRegionAccess(SysUser user, Long regionId) {
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (isSuperAdmin(user)) {
            return;
        }
        Set<Long> allowedRegionIds = resolveAllowedRegionIds(user);
        if (regionId == null || allowedRegionIds == null || !allowedRegionIds.contains(regionId)) {
            throw new BusinessException(403, "无权限操作该区域");
        }
    }
}
