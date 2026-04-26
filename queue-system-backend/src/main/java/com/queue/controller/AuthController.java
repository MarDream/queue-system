package com.queue.controller;

import com.queue.common.Result;
import com.queue.dto.LoginRequest;
import com.queue.dto.LoginVO;
import com.queue.dto.PasswordResetConfirmRequest;
import com.queue.dto.PasswordResetRequest;
import com.queue.dto.RegisterRequest;
import com.queue.entity.SysUser;
import com.queue.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService sysUserService;

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginRequest request) {
        return Result.ok(sysUserService.login(request));
    }

    @PostMapping("/register")
    public Result<SysUser> register(@RequestBody RegisterRequest request) {
        return Result.ok(sysUserService.register(request));
    }

    @PostMapping("/password-reset/request")
    public Result<Void> requestPasswordReset(@RequestBody PasswordResetRequest request) {
        sysUserService.requestPasswordReset(request);
        return Result.ok();
    }

    @PostMapping("/password-reset/confirm")
    public Result<Void> confirmPasswordReset(@RequestBody PasswordResetConfirmRequest request) {
        sysUserService.confirmPasswordReset(request);
        return Result.ok();
    }

    @PostMapping("/password/change")
    public Result<Void> changePassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        String oldPassword = body == null ? null : body.get("oldPassword");
        String newPassword = body == null ? null : body.get("newPassword");
        sysUserService.changePassword(userId, oldPassword, newPassword);
        return Result.ok();
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            sysUserService.logout(token);
        }
        return Result.ok();
    }

    @GetMapping("/userinfo")
    public Result<SysUser> getUserInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId != null) {
            return Result.ok(sysUserService.getById(userId));
        }
        return Result.error(401, "未登录");
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
