package com.queue.service;

import com.queue.dto.LoginRequest;
import com.queue.dto.LoginVO;
import com.queue.dto.SysUserDTO;
import com.queue.dto.UserMenuSortDTO;
import com.queue.dto.UserPermissionDTO;
import com.queue.entity.SysButton;
import com.queue.entity.SysMenu;
import com.queue.entity.SysUser;

import java.util.List;

public interface SysUserService {
    LoginVO login(LoginRequest request);
    void logout(String token);
    SysUser getById(Long id);
    List<SysUser> listAll();
    List<SysUser> listByOperator(Long operatorId);
    SysUser create(SysUserDTO dto);
    SysUser update(SysUserDTO dto);
    void delete(Long id);
    String resetPassword(Long id);

    String resetPasswordWithCustomPassword(Long id, String customPassword);
    void updateMenuSort(UserMenuSortDTO dto);
    List<String> getMenuPathsByRole(String role);
    List<String> getButtonCodesByRole(String role);

    // 用户级权限管理
    void setUserPermissions(Long operatorId, UserPermissionDTO dto);
    UserPermissionDTO getUserPermissions(Long userId);
    List<SysMenu> getAvailableMenusForOperator(Long operatorId);
    List<SysButton> getAvailableButtonsForOperator(Long operatorId);
    List<SysMenu> getAvailableMenusForTargetUser(Long operatorId, Long targetUserId);
    List<SysButton> getAvailableButtonsForTargetUser(Long operatorId, Long targetUserId);
}
