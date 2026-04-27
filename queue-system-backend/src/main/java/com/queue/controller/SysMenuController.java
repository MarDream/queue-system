package com.queue.controller;

import com.queue.common.BusinessException;
import com.queue.common.Result;
import com.queue.common.ResultCode;
import com.queue.dto.UserMenuSortDTO;
import com.queue.entity.SysButton;
import com.queue.entity.SysMenu;
import com.queue.entity.SysUser;
import com.queue.mapper.SysButtonMapper;
import com.queue.mapper.SysPermissionMapper;
import com.queue.mapper.SysUserMapper;
import com.queue.mapper.SysUserMenuMapper;
import com.queue.service.SysMenuService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/menus")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService sysMenuService;
    private final SysUserMapper sysUserMapper;
    private final SysButtonMapper sysButtonMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysUserMenuMapper sysUserMenuMapper;

    @GetMapping
    public Result<List<SysMenu>> list(@RequestParam(required = false) Long userId) {
        List<SysMenu> allMenus = sysMenuService.listAll();
        if (userId == null || userId <= 0) {
            return Result.ok(allMenus); // 不过滤（菜单管理页面）
        }
        // 按用户权限过滤
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || "SUPER_ADMIN".equals(user.getRole())) {
            return Result.ok(allMenus);
        }
        // 获取用户有权限的菜单ID
        List<Long> menuIds = getMenuIdsForUser(userId, user.getRole());
        if (menuIds.isEmpty()) {
            return Result.ok(List.of());
        }
        Set<Long> menuIdSet = new HashSet<>(menuIds);
        // 还需要包含有权限菜单的父级菜单（用于树形展示）
        for (SysMenu m : allMenus) {
            if (menuIdSet.contains(m.getId()) && m.getParentId() != null) {
                menuIdSet.add(m.getParentId());
            }
        }
        List<SysMenu> filtered = allMenus.stream()
                .filter(m -> menuIdSet.contains(m.getId()))
                .collect(java.util.stream.Collectors.toList());
        return Result.ok(filtered);
    }

    private List<Long> getMenuIdsForUser(Long userId, String role) {
        List<Long> menuIds = sysUserMenuMapper.selectMenuIdsByUserId(userId);
        if (menuIds == null || menuIds.isEmpty()) {
            menuIds = sysPermissionMapper.selectMenuIdsByRole(role);
        } else if (menuIds.contains(0L)) {
            menuIds = menuIds.stream().filter(id -> id != 0L).collect(java.util.stream.Collectors.toList());
        }
        return menuIds != null ? menuIds : List.of();
    }

    // 更新当前用户的菜单排序
    @PutMapping("/sort")
    public Result<Void> updateSort(@RequestBody UserMenuSortDTO dto, HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        dto.setUserId(userId);
        sysMenuService.updateSortForUser(dto);
        return Result.ok();
    }

    // 重命名菜单（仅超级管理员）
    @PutMapping("/{id}/rename")
    public Result<Void> rename(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        Long operatorId = (Long) req.getAttribute("userId");
        SysUser operator = sysUserMapper.selectById(operatorId);
        if (operator == null || !"SUPER_ADMIN".equals(operator.getRole())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "仅超级管理员可重命名菜单");
        }
        String newName = body.get("name");
        if (newName == null || newName.trim().isEmpty()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "菜单名称不能为空");
        }
        sysMenuService.renameMenu(id, newName.trim());
        return Result.ok();
    }

    // 更新菜单的父级（拖拽为子菜单）
    @PutMapping("/{id}/parent")
    public Result<Void> updateParent(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long parentId = body.get("parentId");
        sysMenuService.updateMenuParent(id, parentId);
        return Result.ok();
    }

    // 创建分组（仅超级管理员）
    @PostMapping("/group")
    public Result<Void> createGroup(@RequestBody Map<String, String> body, HttpServletRequest req) {
        Long operatorId = (Long) req.getAttribute("userId");
        SysUser operator = sysUserMapper.selectById(operatorId);
        if (operator == null || !"SUPER_ADMIN".equals(operator.getRole())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "仅超级管理员可创建分组");
        }
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "分组名称不能为空");
        }
        sysMenuService.createGroup(name.trim());
        return Result.ok();
    }

    // 删除分组（仅超级管理员，且分组下无子菜单）
    @DeleteMapping("/group/{id}")
    public Result<Void> deleteGroup(@PathVariable Long id, HttpServletRequest req) {
        Long operatorId = (Long) req.getAttribute("userId");
        SysUser operator = sysUserMapper.selectById(operatorId);
        if (operator == null || !"SUPER_ADMIN".equals(operator.getRole())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "仅超级管理员可删除分组");
        }
        sysMenuService.deleteGroup(id);
        return Result.ok();
    }

    // 获取单个菜单详情
    @GetMapping("/{id}")
    public Result<SysMenu> getById(@PathVariable Long id) {
        return Result.ok(sysMenuService.getById(id));
    }

    // 创建菜单（仅超级管理员）
    @PostMapping
    public Result<SysMenu> create(@RequestBody SysMenu menu, HttpServletRequest req) {
        Long operatorId = (Long) req.getAttribute("userId");
        SysUser operator = sysUserMapper.selectById(operatorId);
        if (operator == null || !"SUPER_ADMIN".equals(operator.getRole())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "仅超级管理员可创建菜单");
        }
        if (menu.getName() == null || menu.getName().trim().isEmpty()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "菜单名称不能为空");
        }
        menu.setName(menu.getName().trim());
        return Result.ok(sysMenuService.createMenu(menu));
    }

    // 更新菜单（仅超级管理员）
    @PutMapping("/{id}")
    public Result<SysMenu> update(@PathVariable Long id, @RequestBody SysMenu menu, HttpServletRequest req) {
        Long operatorId = (Long) req.getAttribute("userId");
        SysUser operator = sysUserMapper.selectById(operatorId);
        if (operator == null || !"SUPER_ADMIN".equals(operator.getRole())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "仅超级管理员可修改菜单");
        }
        menu.setId(id);
        return Result.ok(sysMenuService.updateMenu(menu));
    }

    // 删除菜单（仅超级管理员）
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        Long operatorId = (Long) req.getAttribute("userId");
        SysUser operator = sysUserMapper.selectById(operatorId);
        if (operator == null || !"SUPER_ADMIN".equals(operator.getRole())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "仅超级管理员可删除菜单");
        }
        sysMenuService.deleteMenu(id);
        return Result.ok();
    }

    // 创建按钮权限（仅超级管理员）
    @PostMapping("/buttons")
    public Result<SysButton> createButton(@RequestBody SysButton button, HttpServletRequest req) {
        Long operatorId = (Long) req.getAttribute("userId");
        SysUser operator = sysUserMapper.selectById(operatorId);
        if (operator == null || !"SUPER_ADMIN".equals(operator.getRole())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "仅超级管理员可创建按钮权限");
        }
        if (button.getName() == null || button.getName().trim().isEmpty()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "按钮名称不能为空");
        }
        if (button.getMenuId() == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "菜单ID不能为空");
        }
        if (button.getCode() == null || button.getCode().trim().isEmpty()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "按钮编码不能为空");
        }
        sysButtonMapper.insert(button);
        // 自动关联超级管理员角色
        sysPermissionMapper.insertRoleButton("SUPER_ADMIN", button.getId());
        return Result.ok(button);
    }
}
