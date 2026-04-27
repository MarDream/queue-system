package com.queue.controller;

import com.queue.common.BusinessException;
import com.queue.common.Result;
import com.queue.common.ResultCode;
import com.queue.dto.SysRoleDTO;
import com.queue.entity.SysMenu;
import com.queue.entity.SysRole;
import com.queue.mapper.SysRoleButtonMapper;
import com.queue.mapper.SysRoleMenuMapper;
import com.queue.service.SysMenuService;
import com.queue.service.SysRoleService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class RoleController {

    private final SysRoleService sysRoleService;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysRoleButtonMapper sysRoleButtonMapper;
    private final SysMenuService sysMenuService;
    private final com.queue.mapper.SysButtonMapper sysButtonMapper;

    @GetMapping
    public Result<List<SysRole>> list() {
        return Result.ok(sysRoleService.listAll());
    }

    @GetMapping("/{id}")
    public Result<SysRole> getById(@PathVariable Long id) {
        return Result.ok(sysRoleService.getById(id));
    }

    @PostMapping
    public Result<SysRole> create(@RequestBody SysRoleDTO dto, HttpServletRequest req) {
        validateSuperAdmin(req);
        return Result.ok(sysRoleService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<SysRole> update(@PathVariable Long id, @RequestBody SysRoleDTO dto, HttpServletRequest req) {
        validateSuperAdmin(req);
        dto.setId(id);
        return Result.ok(sysRoleService.update(dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        validateSuperAdmin(req);
        sysRoleService.delete(id);
        return Result.ok();
    }

    @GetMapping("/menus")
    public Result<List<SysMenu>> listAllMenus() {
        return Result.ok(sysMenuService.listAll());
    }

    @GetMapping("/buttons")
    public Result<List<com.queue.entity.SysButton>> listAllButtons() {
        var qw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.queue.entity.SysButton>();
        qw.eq(com.queue.entity.SysButton::getDeleted, 0)
          .orderByAsc(com.queue.entity.SysButton::getSortOrder);
        return Result.ok(sysButtonMapper.selectList(qw));
    }

    @GetMapping("/{code}/menus")
    public Result<List<Long>> getRoleMenus(@PathVariable String code) {
        List<Long> menuIds = sysRoleMenuMapper.selectMenuIdsByRole(code);
        return Result.ok(menuIds != null ? menuIds : List.of());
    }

    @GetMapping("/{code}/buttons")
    public Result<List<Long>> getRoleButtons(@PathVariable String code) {
        List<Long> buttonIds = sysRoleButtonMapper.selectButtonIdsByRole(code);
        return Result.ok(buttonIds != null ? buttonIds : List.of());
    }

    @PutMapping("/{code}/menus")
    @Transactional
    public Result<Void> updateRoleMenus(
            @PathVariable String code,
            @RequestBody Map<String, List<Long>> body,
            HttpServletRequest req) {
        validateSuperAdmin(req);
        List<Long> menuIds = body.get("menuIds");
        if (menuIds == null) menuIds = List.of();

        SysRole role = sysRoleService.getByCode(code);
        if (role == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "角色不存在");
        }
        sysRoleMenuMapper.deleteByRoleCode(code);
        for (Long menuId : menuIds) {
            sysRoleMenuMapper.insertRoleMenu(role.getId(), code, menuId);
        }
        return Result.ok();
    }

    @PutMapping("/{code}/permissions")
    @Transactional
    public Result<Void> updateRolePermissions(
            @PathVariable String code,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        validateSuperAdmin(req);

        List<Long> menuIds = toLongList(body.get("menuIds"));
        List<Long> buttonIds = toLongList(body.get("buttonIds"));

        SysRole role = sysRoleService.getByCode(code);
        if (role == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "角色不存在");
        }
        sysRoleMenuMapper.deleteByRoleCode(code);
        for (Long menuId : menuIds) {
            sysRoleMenuMapper.insertRoleMenu(role.getId(), code, menuId);
        }

        sysRoleButtonMapper.deleteByRoleCode(code);
        for (Long buttonId : buttonIds) {
            sysRoleButtonMapper.insertRoleButton(role.getId(), code, buttonId);
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

    private void validateSuperAdmin(HttpServletRequest req) {
        String role = (String) req.getAttribute("role");
        if (!"SUPER_ADMIN".equals(role)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅超级管理员可操作");
        }
    }
}
