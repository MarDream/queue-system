package com.queue.controller;

import com.queue.common.BusinessException;
import com.queue.common.Result;
import com.queue.common.ResultCode;
import com.queue.entity.SysMenu;
import com.queue.entity.SysUser;
import com.queue.mapper.SysRoleButtonMapper;
import com.queue.mapper.SysRoleMenuMapper;
import com.queue.mapper.SysUserMapper;
import com.queue.service.SysMenuService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class RolePermissionController {

    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysRoleButtonMapper sysRoleButtonMapper;
    private final SysUserMapper sysUserMapper;
    private final SysMenuService sysMenuService;
    private final com.queue.mapper.SysButtonMapper sysButtonMapper;

    /**
     * 获取所有角色列表
     */
    @GetMapping
    public Result<List<Map<String, String>>> listRoles() {
        List<Map<String, String>> roles = List.of(
            Map.of("value", "SUPER_ADMIN", "label", "超级管理员"),
            Map.of("value", "REGION_ADMIN", "label", "区域管理员"),
            Map.of("value", "WINDOW_OPERATOR", "label", "窗口操作员")
        );
        return Result.ok(roles);
    }

    /**
     * 获取所有菜单列表
     */
    @GetMapping("/menus")
    public Result<List<SysMenu>> listAllMenus() {
        return Result.ok(sysMenuService.listAll());
    }

    /**
     * 获取所有按钮列表（用于角色权限配置）
     */
    @GetMapping("/buttons")
    public Result<List<com.queue.entity.SysButton>> listAllButtons() {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.queue.entity.SysButton> qw =
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        qw.eq(com.queue.entity.SysButton::getDeleted, 0)
          .orderByAsc(com.queue.entity.SysButton::getSortOrder);
        return Result.ok(sysButtonMapper.selectList(qw));
    }

    /**
     * 获取指定角色的菜单权限
     */
    @GetMapping("/{role}/menus")
    public Result<List<Long>> getRoleMenus(@PathVariable String role) {
        List<Long> menuIds = sysRoleMenuMapper.selectMenuIdsByRole(role);
        return Result.ok(menuIds != null ? menuIds : List.of());
    }

    /**
     * 获取指定角色的按钮权限
     */
    @GetMapping("/{role}/buttons")
    public Result<List<Long>> getRoleButtons(@PathVariable String role) {
        List<Long> buttonIds = sysRoleButtonMapper.selectButtonIdsByRole(role);
        return Result.ok(buttonIds != null ? buttonIds : List.of());
    }

    /**
     * 更新角色菜单权限
     */
    @PutMapping("/{role}/menus")
    @Transactional
    public Result<Void> updateRoleMenus(
            @PathVariable String role,
            @RequestBody Map<String, List<Long>> body,
            HttpServletRequest req) {
        // 权限校验
        validateSuperAdmin(req);

        List<Long> menuIds = body.get("menuIds");
        if (menuIds == null) {
            menuIds = List.of();
        }

        // 删除现有菜单权限
        sysRoleMenuMapper.deleteByRole(role);
        // 插入新权限
        for (Long menuId : menuIds) {
            sysRoleMenuMapper.insertRoleMenu(role, menuId);
        }

        return Result.ok();
    }

    /**
     * 批量更新角色完整权限（菜单+按钮）
     */
    @PutMapping("/{role}/permissions")
    @Transactional
    public Result<Void> updateRolePermissions(
            @PathVariable String role,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        // 权限校验
        validateSuperAdmin(req);

        List<Long> menuIds = toLongList(body.get("menuIds"));
        List<Long> buttonIds = toLongList(body.get("buttonIds"));

        // 更新菜单权限
        sysRoleMenuMapper.deleteByRole(role);
        for (Long menuId : menuIds) {
            sysRoleMenuMapper.insertRoleMenu(role, menuId);
        }

        // 更新按钮权限
        sysRoleButtonMapper.deleteByRole(role);
        for (Long buttonId : buttonIds) {
            sysRoleButtonMapper.insertRoleButton(role, buttonId);
        }

        return Result.ok();
    }

    private List<Long> toLongList(Object value) {
        if (!(value instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }
        return rawList.stream()
                .filter(item -> item != null)
                .map(item -> {
                    if (item instanceof Number number) {
                        return number.longValue();
                    }
                    return Long.parseLong(String.valueOf(item));
                })
                .toList();
    }

    /**
     * 权限校验：仅超级管理员可操作
     */
    private void validateSuperAdmin(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "请先登录");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !"SUPER_ADMIN".equals(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅超级管理员可操作角色权限");
        }
    }
}
