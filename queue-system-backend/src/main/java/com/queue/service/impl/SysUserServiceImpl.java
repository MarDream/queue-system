package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.queue.common.BusinessException;
import com.queue.common.ResultCode;
import com.queue.dto.LoginRequest;
import com.queue.dto.LoginVO;
import com.queue.dto.SysUserDTO;
import com.queue.dto.UserMenuSortDTO;
import com.queue.dto.UserPermissionDTO;
import com.queue.entity.SysButton;
import com.queue.entity.SysMenu;
import com.queue.entity.SysUser;
import com.queue.entity.SysUserMenu;
import com.queue.entity.SysUserButton;
import com.queue.entity.Region;
import com.queue.mapper.RegionMapper;
import com.queue.mapper.SysButtonMapper;
import com.queue.mapper.SysMenuMapper;
import com.queue.mapper.SysPermissionMapper;
import com.queue.mapper.SysUserMapper;
import com.queue.mapper.SysUserMenuMapper;
import com.queue.mapper.SysUserButtonMapper;
import com.queue.service.SysUserService;
import com.queue.service.TicketService;
import com.queue.util.JwtUtil;
import com.queue.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysButtonMapper sysButtonMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysUserMenuMapper sysUserMenuMapper;
    private final SysUserButtonMapper sysUserButtonMapper;
    private final RegionMapper regionMapper;
    private final JwtUtil jwtUtil;
    private final TicketService ticketService;

    @Override
    public LoginVO login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername())
                .eq(SysUser::getDeleted, 0)
        );

        if (user == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "用户名或密码错误");
        }

        if (user.getStatus() != 1) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "账号已被禁用");
        }

        if (!verifyPassword(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "用户名或密码错误");
        }

        user.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        List<String> menuPaths = getMenuPaths(user.getId(), user.getRole());
        List<String> buttonCodes = getButtonCodes(user.getId(), user.getRole());

        LoginVO vo = new LoginVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setName(user.getName());
        vo.setRole(user.getRole());
        vo.setRegionId(user.getRegionId());
        vo.setRegionCode(user.getRegionCode());
        vo.setToken(token);
        vo.setMenuPaths(menuPaths);
        vo.setButtonCodes(buttonCodes);

        // 查询区域名称
        if (user.getRegionId() != null) {
            Region region = regionMapper.selectById(user.getRegionId());
            if (region != null) {
                vo.setRegionName(region.getRegionName());
            }
        }

        // 窗口操作员登录时，自动扫描历史未办结票并标记为过号
        if ("WINDOW_OPERATOR".equals(user.getRole())) {
            int skipped = ticketService.markExpiredTickets();
            if (skipped > 0) {
                System.out.println("INFO: 登录时扫描到 " + skipped + " 张历史未办结票，已自动标记为过号");
            }
        }

        return vo;
    }

    private boolean verifyPassword(String rawPassword, String encodedPassword) {
        // If password starts with $2a$, $2b$, or $2y$, it's BCrypt encoded
        if (encodedPassword != null && encodedPassword.startsWith("$2")) {
            try {
                org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
                    new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
                return encoder.matches(rawPassword, encodedPassword);
            } catch (Exception e) {
                return false;
            }
        }
        // Otherwise, compare plaintext (for initial testing)
        return rawPassword.equals(encodedPassword);
    }

    @Override
    public void logout(String token) {
    }

    @Override
    public SysUser getById(Long id) {
        return sysUserMapper.selectById(id);
    }

    @Override
    public List<SysUser> listAll() {
        return sysUserMapper.selectList(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .orderByAsc(SysUser::getCreatedAt)
        );
    }

    @Override
    public List<SysUser> listByOperator(Long operatorId) {
        if (operatorId == null) {
            return listAll();
        }
        SysUser operator = sysUserMapper.selectById(operatorId);
        if (operator == null) {
            return new ArrayList<>();
        }
        // 超级管理员看全部
        if ("SUPER_ADMIN".equals(operator.getRole())) {
            return listAll();
        }
        // 区域管理员按区域层级过滤
        if ("REGION_ADMIN".equals(operator.getRole())) {
            List<Long> allowedRegionIds = getAllowedRegionIds(operator.getRegionId());
            if (allowedRegionIds == null || allowedRegionIds.isEmpty()) {
                return new ArrayList<>();
            }
            return sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getDeleted, 0)
                    .in(SysUser::getRegionId, allowedRegionIds)
                    .orderByAsc(SysUser::getCreatedAt)
            );
        }
        // 其他角色只看自己
        return sysUserMapper.selectList(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .eq(SysUser::getId, operatorId)
                .orderByAsc(SysUser::getCreatedAt)
        );
    }

    /**
     * 获取指定区域及其所有子层级的区域ID列表
     */
    private List<Long> getAllowedRegionIds(Long regionId) {
        if (regionId == null) {
            return new ArrayList<>();
        }
        List<Long> result = new ArrayList<>();
        result.add(regionId);
        // 递归获取所有子区域
        addChildRegionIds(regionId, result);
        return result;
    }

    /**
     * 递归添加子区域ID
     */
    private void addChildRegionIds(Long parentId, List<Long> result) {
        List<Region> children = regionMapper.selectList(
            new LambdaQueryWrapper<Region>()
                .eq(Region::getParentId, parentId)
                .eq(Region::getDeleted, 0)
        );
        for (Region child : children) {
            result.add(child.getId());
            addChildRegionIds(child.getId(), result);
        }
    }

    @Override
    @Transactional
    public SysUser create(SysUserDTO dto) {
        SysUser exist = sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername())
                .eq(SysUser::getDeleted, 0)
        );
        if (exist != null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(PasswordUtil.encodeBCrypt(dto.getPassword()));
        user.setName(dto.getName());
        user.setRole(dto.getRole());
        user.setRegionId(dto.getRegionId());
        user.setRegionCode(dto.getRegionCode());
        user.setStatus(1);

        sysUserMapper.insert(user);
        return user;
    }

    @Override
    @Transactional
    public SysUser update(SysUserDTO dto) {
        SysUser user = sysUserMapper.selectById(dto.getId());
        if (user == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "用户不存在");
        }

        user.setName(dto.getName());
        user.setRole(dto.getRole());
        user.setRegionId(dto.getRegionId());
        user.setRegionCode(dto.getRegionCode());
        user.setStatus(dto.getStatus());

        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPassword(PasswordUtil.encodeBCrypt(dto.getPassword()));
        }

        sysUserMapper.updateById(user);
        return user;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "用户不存在");
        }
        // 使用 MyBatis-Plus 的 deleteById（配合 @TableLogic 会执行逻辑删除）
        sysUserMapper.deleteById(id);
    }

    @Override
    @Transactional
    public String resetPassword(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "用户不存在");
        }

        String newPassword = PasswordUtil.generateRandomPassword(10);
        user.setPassword(PasswordUtil.encodeBCrypt(newPassword));
        sysUserMapper.updateById(user);

        return newPassword;
    }

    @Override
    @Transactional
    public String resetPasswordWithCustomPassword(Long id, String customPassword) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "用户不存在");
        }

        user.setPassword(PasswordUtil.encodeBCrypt(customPassword));
        sysUserMapper.updateById(user);

        return customPassword;
    }

    @Override
    @Transactional
    public void updateMenuSort(UserMenuSortDTO dto) {
    }

    @Override
    public List<String> getMenuPathsByRole(String role) {
        List<Long> menuIds = sysPermissionMapper.selectMenuIdsByRole(role);
        if (menuIds == null || menuIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<SysMenu> menus = sysMenuMapper.selectList(
            new LambdaQueryWrapper<SysMenu>()
                .in(SysMenu::getId, menuIds)
                .orderByAsc(SysMenu::getSortOrder)
        );

        return menus.stream()
            .map(SysMenu::getPath)
            .collect(Collectors.toList());
    }

    @Override
    public List<String> getButtonCodesByRole(String role) {
        List<Long> buttonIds = sysPermissionMapper.selectButtonIdsByRole(role);
        if (buttonIds == null || buttonIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<SysButton> buttons = sysButtonMapper.selectList(
            new LambdaQueryWrapper<SysButton>()
                .in(SysButton::getId, buttonIds)
                .orderByAsc(SysButton::getSortOrder)
        );

        return buttons.stream()
            .map(SysButton::getCode)
            .collect(Collectors.toList());
    }

    // ==================== 用户级权限管理 ====================

    /**
     * 获取用户的菜单路径（优先用户级，回退角色级）
     */
    private List<String> getMenuPaths(Long userId, String role) {
        List<Long> menuIds = sysUserMenuMapper.selectMenuIdsByUserId(userId);
        if (menuIds == null || menuIds.isEmpty()) {
            menuIds = sysPermissionMapper.selectMenuIdsByRole(role);
        }
        if (menuIds == null || menuIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<SysMenu> menus = sysMenuMapper.selectList(
            new LambdaQueryWrapper<SysMenu>()
                .in(SysMenu::getId, menuIds)
                .eq(SysMenu::getDeleted, 0)
                .orderByAsc(SysMenu::getSortOrder)
        );

        return menus.stream()
            .map(SysMenu::getPath)
            .collect(Collectors.toList());
    }

    /**
     * 获取用户的按钮编码（优先用户级，回退角色级）
     */
    private List<String> getButtonCodes(Long userId, String role) {
        List<Long> buttonIds = sysUserButtonMapper.selectButtonIdsByUserId(userId);
        if (buttonIds == null || buttonIds.isEmpty()) {
            buttonIds = sysPermissionMapper.selectButtonIdsByRole(role);
        }
        if (buttonIds == null || buttonIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<SysButton> buttons = sysButtonMapper.selectList(
            new LambdaQueryWrapper<SysButton>()
                .in(SysButton::getId, buttonIds)
                .eq(SysButton::getDeleted, 0)
                .orderByAsc(SysButton::getSortOrder)
        );

        return buttons.stream()
            .map(SysButton::getCode)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void setUserPermissions(Long operatorId, UserPermissionDTO dto) {
        SysUser operator = sysUserMapper.selectById(operatorId);
        SysUser target = sysUserMapper.selectById(dto.getUserId());
        if (target == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "用户不存在");
        }

        // 权限校验
        validatePermission(operator, target, dto);

        // 删除旧权限
        sysUserMenuMapper.delete(new LambdaQueryWrapper<SysUserMenu>().eq(SysUserMenu::getUserId, dto.getUserId()));
        sysUserButtonMapper.delete(new LambdaQueryWrapper<SysUserButton>().eq(SysUserButton::getUserId, dto.getUserId()));

        // 插入"已配置"标记（menuId=0 表示用户级权限已明确设置）
        SysUserMenu marker = new SysUserMenu();
        marker.setUserId(dto.getUserId());
        marker.setMenuId(0L);
        sysUserMenuMapper.insert(marker);

        SysUserButton btnMarker = new SysUserButton();
        btnMarker.setUserId(dto.getUserId());
        btnMarker.setButtonId(0L);
        sysUserButtonMapper.insert(btnMarker);

        // 插入新权限
        if (dto.getMenuIds() != null) {
            for (Long menuId : dto.getMenuIds()) {
                SysUserMenu um = new SysUserMenu();
                um.setUserId(dto.getUserId());
                um.setMenuId(menuId);
                sysUserMenuMapper.insert(um);
            }
        }
        if (dto.getButtonIds() != null) {
            for (Long buttonId : dto.getButtonIds()) {
                SysUserButton ub = new SysUserButton();
                ub.setUserId(dto.getUserId());
                ub.setButtonId(buttonId);
                sysUserButtonMapper.insert(ub);
            }
        }
    }

    /**
     * 校验操作者的权限是否合法
     */
    private void validatePermission(SysUser operator, SysUser target, UserPermissionDTO dto) {
        if ("SUPER_ADMIN".equals(operator.getRole())) {
            // 超级管理员可以配置任何人
            return;
        }

        if ("REGION_ADMIN".equals(operator.getRole())) {
            // 区域管理员只能配置窗口操作员
            if (!"WINDOW_OPERATOR".equals(target.getRole())) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "只能为窗口操作员分配权限");
            }
            // 区域管理员不能超出自身权限范围
            List<Long> operatorMenuIds = getMenuIdsForUser(operator.getId(), operator.getRole());
            List<Long> operatorButtonIds = getButtonIdsForUser(operator.getId(), operator.getRole());
            if (dto.getMenuIds() != null) {
                for (Long menuId : dto.getMenuIds()) {
                    if (!operatorMenuIds.contains(menuId)) {
                        throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "无权分配该菜单权限");
                    }
                }
            }
            if (dto.getButtonIds() != null) {
                for (Long buttonId : dto.getButtonIds()) {
                    if (!operatorButtonIds.contains(buttonId)) {
                        throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "无权分配该按钮权限");
                    }
                }
            }
            return;
        }

        throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "无权限操作");
    }

    /**
     * 获取用户实际拥有的菜单ID（用户级 > 角色级）
     * menuId=0 为"已配置"标记，表示用户级权限已明确设置（包括空权限）
     */
    private List<Long> getMenuIdsForUser(Long userId, String role) {
        List<Long> menuIds = sysUserMenuMapper.selectMenuIdsByUserId(userId);
        if (menuIds == null || menuIds.isEmpty()) {
            // 从未配置过，回退到角色级
            menuIds = sysPermissionMapper.selectMenuIdsByRole(role);
        } else if (menuIds.contains(0L)) {
            // 有配置标记，去掉标记只返回实际权限
            menuIds = menuIds.stream().filter(id -> id != 0L).collect(java.util.stream.Collectors.toList());
        }
        // 否则：用户级权限非空且无标记，直接使用
        return menuIds != null ? menuIds : new ArrayList<>();
    }

    /**
     * 获取用户实际拥有的按钮ID（用户级 > 角色级）
     * buttonId=0 为"已配置"标记，同菜单逻辑
     */
    private List<Long> getButtonIdsForUser(Long userId, String role) {
        List<Long> buttonIds = sysUserButtonMapper.selectButtonIdsByUserId(userId);
        if (buttonIds == null || buttonIds.isEmpty()) {
            // 从未配置过，回退到角色级
            buttonIds = sysPermissionMapper.selectButtonIdsByRole(role);
        } else if (buttonIds.contains(0L)) {
            // 有配置标记，去掉标记只返回实际权限
            buttonIds = buttonIds.stream().filter(id -> id != 0L).collect(java.util.stream.Collectors.toList());
        }
        return buttonIds != null ? buttonIds : new ArrayList<>();
    }

    @Override
    public UserPermissionDTO getUserPermissions(Long userId) {
        UserPermissionDTO dto = new UserPermissionDTO();
        dto.setUserId(userId);
        List<Long> menuIds = sysUserMenuMapper.selectMenuIdsByUserId(userId);
        List<Long> buttonIds = sysUserButtonMapper.selectButtonIdsByUserId(userId);
        // 过滤掉配置标记（id=0）
        dto.setMenuIds(menuIds != null ? menuIds.stream().filter(id -> id != 0L).collect(java.util.stream.Collectors.toList()) : null);
        dto.setButtonIds(buttonIds != null ? buttonIds.stream().filter(id -> id != 0L).collect(java.util.stream.Collectors.toList()) : null);
        return dto;
    }

    @Override
    public List<SysMenu> getAvailableMenusForOperator(Long operatorId) {
        SysUser operator = sysUserMapper.selectById(operatorId);
        if (operator == null) {
            return new ArrayList<>();
        }
        List<Long> menuIds = getMenuIdsForUser(operatorId, operator.getRole());
        if (menuIds.isEmpty()) {
            return new ArrayList<>();
        }
        return sysMenuMapper.selectList(
            new LambdaQueryWrapper<SysMenu>()
                .in(SysMenu::getId, menuIds)
                .eq(SysMenu::getDeleted, 0)
                .orderByAsc(SysMenu::getSortOrder)
        );
    }

    @Override
    public List<SysButton> getAvailableButtonsForOperator(Long operatorId) {
        SysUser operator = sysUserMapper.selectById(operatorId);
        if (operator == null) {
            return new ArrayList<>();
        }
        List<Long> buttonIds = getButtonIdsForUser(operatorId, operator.getRole());
        if (buttonIds.isEmpty()) {
            return new ArrayList<>();
        }
        return sysButtonMapper.selectList(
            new LambdaQueryWrapper<SysButton>()
                .in(SysButton::getId, buttonIds)
                .eq(SysButton::getDeleted, 0)
                .orderByAsc(SysButton::getSortOrder)
        );
    }
}
