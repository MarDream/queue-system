package com.queue.controller;

import com.queue.common.Result;
import com.queue.common.ResultCode;
import com.queue.dto.SysUserDTO;
import com.queue.dto.ResetPasswordRequest;
import com.queue.dto.UserPermissionDTO;
import com.queue.entity.SysButton;
import com.queue.entity.SysMenu;
import com.queue.entity.SysUser;
import com.queue.service.SysUserService;
import com.queue.util.PasswordUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    public Result<List<SysUser>> list(jakarta.servlet.http.HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.ok(sysUserService.listByOperator(operatorId));
    }

    @GetMapping("/{id}")
    public Result<SysUser> getById(@PathVariable Long id) {
        return Result.ok(sysUserService.getById(id));
    }

    @PostMapping
    public Result<SysUser> create(@RequestBody SysUserDTO dto, jakarta.servlet.http.HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        SysUser operator = operatorId == null ? null : sysUserService.getById(operatorId);
        if (operator == null) {
            return Result.error(401, "未登录");
        }
        if ("SUPER_ADMIN".equals(dto.getRole())) {
            return Result.error(400, "不允许创建超级管理员账号");
        }
        if ("REGION_ADMIN".equals(operator.getRole())) {
            if ("REGION_ADMIN".equals(dto.getRole())) {
                return Result.error(400, "区域管理员不允许创建区域管理员账号");
            }
        }
        if (dto.getStatus() == null || dto.getStatus() == 0) {
            dto.setStatus(1);
        }
        return Result.ok(sysUserService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<SysUser> update(@PathVariable Long id, @RequestBody SysUserDTO dto, jakarta.servlet.http.HttpServletRequest request) {
        dto.setId(id);
        Long operatorId = (Long) request.getAttribute("userId");
        SysUser operator = operatorId == null ? null : sysUserService.getById(operatorId);
        if (operator == null) {
            return Result.error(401, "未登录");
        }
        SysUser target = sysUserService.getById(id);
        if (target == null) {
            return Result.error(400, "用户不存在");
        }

        if ("SUPER_ADMIN".equals(operator.getRole())) {
            if (operatorId != null && operatorId.equals(id) && dto.getStatus() != null && dto.getStatus() == 2) {
                return Result.error(400, "超级管理员不能禁用自己");
            }
            if ("SUPER_ADMIN".equals(target.getRole())) {
                if (dto.getStatus() != null && dto.getStatus() == 2) {
                    return Result.error(400, "不允许禁用超级管理员");
                }
                if (dto.getRole() != null && !dto.getRole().isBlank() && !"SUPER_ADMIN".equals(dto.getRole())) {
                    return Result.error(400, "不允许修改超级管理员角色");
                }
            }
        }

        if ("SUPER_ADMIN".equals(dto.getRole())) {
            return Result.error(400, "不允许将用户设置为超级管理员");
        }
        if ("REGION_ADMIN".equals(operator.getRole())) {
            if (!operator.getId().equals(id) && !"WINDOW_OPERATOR".equals(target.getRole())) {
                return Result.error(ResultCode.UNAUTHORIZED.getCode(), "仅允许管理窗口操作员用户");
            }
            if ("REGION_ADMIN".equals(dto.getRole())) {
                return Result.error(400, "区域管理员不允许将用户设置为区域管理员");
            }
        }
        return Result.ok(sysUserService.update(dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        SysUser operator = operatorId == null ? null : sysUserService.getById(operatorId);
        if (operator == null) {
            return Result.error(401, "未登录");
        }
        if (!"SUPER_ADMIN".equals(operator.getRole())) {
            return Result.error(ResultCode.UNAUTHORIZED.getCode(), "仅超级管理员可删除用户");
        }
        if (operator.getId().equals(id)) {
            return Result.error(400, "超级管理员不能删除自己");
        }
        SysUser target = sysUserService.getById(id);
        if (target == null) {
            return Result.error(400, "用户不存在");
        }
        if ("SUPER_ADMIN".equals(target.getRole())) {
            return Result.error(400, "不允许删除超级管理员");
        }
        sysUserService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/reset-password")
    public Result<String> resetPassword(@PathVariable Long id, @RequestBody(required = false) ResetPasswordRequest request) {
        String newPassword;
        if (request != null && request.getPassword() != null && !request.getPassword().isBlank()) {
            // 使用指定的密码
            newPassword = sysUserService.resetPasswordWithCustomPassword(id, request.getPassword());
        } else {
            // 生成随机密码
            newPassword = sysUserService.resetPassword(id);
        }
        return Result.ok(newPassword);
    }

    // 临时：直接设置密码（绕过乐观锁问题）
    @PostMapping("/{id}/fix-password")
    public Result<Void> fixPassword(@PathVariable Long id, @RequestBody SysUserDTO dto) {
        String encoded = PasswordUtil.encodeBCrypt(dto.getPassword());
        jdbcTemplate.update("UPDATE sys_user SET password = ?, version = version + 1 WHERE id = ?", encoded, id);
        return Result.ok();
    }

    // 获取用户权限配置
    @GetMapping("/{id}/permissions")
    public Result<UserPermissionDTO> getUserPermissions(@PathVariable Long id) {
        return Result.ok(sysUserService.getUserPermissions(id));
    }

    // 设置用户权限（超级管理员/区域管理员）
    @PutMapping("/{id}/permissions")
    public Result<Void> setUserPermissions(@PathVariable Long id,
                                           @RequestBody UserPermissionDTO dto,
                                           jakarta.servlet.http.HttpServletRequest request) {
        dto.setUserId(id);
        Long operatorId = (Long) request.getAttribute("userId");
        sysUserService.setUserPermissions(operatorId, dto);
        return Result.ok();
    }

    @PostMapping("/{id}/activate")
    public Result<Void> activate(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        if (operatorId == null) {
            return Result.error(401, "未登录");
        }
        sysUserService.activateUser(operatorId, id);
        return Result.ok();
    }

    // 获取当前操作者可授权的菜单列表
    @GetMapping("/available-menus")
    public Result<List<SysMenu>> getAvailableMenus(jakarta.servlet.http.HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.ok(sysUserService.getAvailableMenusForOperator(operatorId));
    }

    // 获取面向目标用户角色后可授权的菜单列表
    @GetMapping("/{id}/available-menus")
    public Result<List<SysMenu>> getAvailableMenusForTarget(@PathVariable Long id,
                                                            jakarta.servlet.http.HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.ok(sysUserService.getAvailableMenusForTargetUser(operatorId, id));
    }

    // 获取面向目标用户角色后可授权的按钮列表
    @GetMapping("/{id}/available-buttons")
    public Result<List<SysButton>> getAvailableButtonsForTarget(@PathVariable Long id,
                                                                jakarta.servlet.http.HttpServletRequest request) {
        Long operatorId = (Long) request.getAttribute("userId");
        return Result.ok(sysUserService.getAvailableButtonsForTargetUser(operatorId, id));
    }
}
