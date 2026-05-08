package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.queue.common.BusinessException;
import com.queue.common.ResultCode;
import com.queue.dto.LoginRequest;
import com.queue.dto.LoginVO;
import com.queue.dto.PasswordResetConfirmRequest;
import com.queue.dto.PasswordResetRequest;
import com.queue.dto.RegisterRequest;
import com.queue.dto.SysUserDTO;
import com.queue.dto.UserImportResult;
import com.queue.dto.UserMenuSortDTO;
import com.queue.dto.UserPermissionDTO;
import com.queue.config.ServerConfig;
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
import com.queue.util.JwtUtil;
import com.queue.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.security.SecureRandom;
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
    private final StringRedisTemplate stringRedisTemplate;
    private final JavaMailSender mailSender;
    private final ServerConfig serverConfig;
    private final Environment env;

    private static final int USER_STATUS_PENDING = 0;
    private static final int USER_STATUS_ACTIVE = 1;
    private static final int USER_STATUS_DISABLED = 2;
    private static final String PWD_RESET_CODE_KEY_PREFIX = "pwdreset:code:";
    private static final String PWD_RESET_SENT_KEY_PREFIX = "pwdreset:sent:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String[] IMPORT_HEADERS = {"用户名", "姓名", "邮箱", "角色", "区域代码", "初始密码", "状态"};

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

        Integer status = user.getStatus();
        if (status == null || status != USER_STATUS_ACTIVE) {
            if (status != null && status == USER_STATUS_PENDING) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "账号待激活");
            }
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

        // 查询区域名称和区域编码
        if (user.getRegionId() != null) {
            Region region = regionMapper.selectById(user.getRegionId());
            if (region != null) {
                vo.setRegionName(region.getRegionName());
                // 如果 regionCode 为空，从 region 表补充
                if (vo.getRegionCode() == null || vo.getRegionCode().isEmpty()) {
                    vo.setRegionCode(region.getRegionCode());
                }
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

        String normalizedEmail = null;
        if (dto.getEmail() != null) {
            String email = dto.getEmail().trim().toLowerCase();
            if (!email.isEmpty()) {
                normalizedEmail = email;
            }
        }
        if (normalizedEmail != null) {
            SysUser existEmail = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmail, normalizedEmail)
                    .eq(SysUser::getDeleted, 0)
            );
            if (existEmail != null) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "邮箱已存在");
            }
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(PasswordUtil.encodeBCrypt(dto.getPassword()));
        user.setName(dto.getName());
        user.setEmail(normalizedEmail);
        user.setRole(dto.getRole());
        user.setRegionId(dto.getRegionId());
        user.setRegionCode(resolveRegionCode(dto.getRegionId()));
        user.setStatus(dto.getStatus() == null ? USER_STATUS_PENDING : dto.getStatus());
        if (user.getStatus() == USER_STATUS_ACTIVE) {
            user.setActivatedAt(LocalDateTime.now());
        }

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
        if (dto.getEmail() != null) {
            String email = dto.getEmail().trim().toLowerCase();
            if (!email.isEmpty()) {
                SysUser existEmail = sysUserMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getEmail, email)
                        .eq(SysUser::getDeleted, 0)
                        .ne(SysUser::getId, user.getId())
                );
                if (existEmail != null) {
                    throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "邮箱已存在");
                }
                user.setEmail(email);
            } else {
                user.setEmail(null);
            }
        }

        if (dto.getRole() != null && !dto.getRole().isBlank()) {
            user.setRole(dto.getRole());
        }
        user.setRegionId(dto.getRegionId());
        user.setRegionCode(resolveRegionCode(dto.getRegionId()));
        if (dto.getStatus() != null) {
            if (user.getStatus() != null && user.getStatus() == USER_STATUS_PENDING && dto.getStatus() == USER_STATUS_ACTIVE) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "待激活账号请通过激活操作启用");
            }
            user.setStatus(dto.getStatus());
            if (dto.getStatus() == USER_STATUS_ACTIVE && (user.getActivatedAt() == null)) {
                user.setActivatedAt(LocalDateTime.now());
            }
        }

        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPassword(PasswordUtil.encodeBCrypt(dto.getPassword()));
        }

        sysUserMapper.updateById(user);
        return user;
    }

    @Override
    @Transactional
    public SysUser register(RegisterRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "参数错误");
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "请输入用户名");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "请输入密码");
        }
        if (request.getPassword().length() < 6) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "密码至少6位");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "请输入邮箱");
        }
        if (request.getRole() == null || request.getRole().isBlank()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "请选择角色");
        }
        if ("SUPER_ADMIN".equals(request.getRole())) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "不允许注册超级管理员角色");
        }
        if (request.getRegionId() == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "请选择注册区域");
        }

        SysUser exist = sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername())
                .eq(SysUser::getDeleted, 0)
        );
        if (exist != null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "用户名已存在");
        }

        String email = request.getEmail().trim().toLowerCase();
        SysUser existEmail = sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, email)
                .eq(SysUser::getDeleted, 0)
        );
        if (existEmail != null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "邮箱已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(PasswordUtil.encodeBCrypt(request.getPassword()));
        user.setName(request.getName());
        user.setEmail(email);
        user.setRole(request.getRole());
        user.setRegionId(request.getRegionId());
        user.setRegionCode(resolveRegionCode(request.getRegionId()));
        user.setStatus(USER_STATUS_PENDING);
        sysUserMapper.insert(user);
        return user;
    }

    @Override
    @Transactional
    public void activateUser(Long operatorId, Long userId) {
        SysUser operator = sysUserMapper.selectById(operatorId);
        if (operator == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "未登录");
        }
        SysUser target = sysUserMapper.selectById(userId);
        if (target == null || target.getDeleted() != null && target.getDeleted() == 1) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "用户不存在");
        }
        if (target.getStatus() != null && target.getStatus() == USER_STATUS_ACTIVE) {
            return;
        }
        if (target.getStatus() == null || target.getStatus() != USER_STATUS_PENDING) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "当前账号状态不允许激活");
        }
        if ("SUPER_ADMIN".equals(operator.getRole())) {
            target.setStatus(USER_STATUS_ACTIVE);
            target.setActivatedBy(operatorId);
            target.setActivatedAt(LocalDateTime.now());
            sysUserMapper.updateById(target);
            return;
        }

        if ("REGION_ADMIN".equals(operator.getRole())) {
            if ("REGION_ADMIN".equals(target.getRole())) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "区域管理员账号只能由超级管理员激活");
            }
            if (operator.getRegionId() == null || target.getRegionId() == null) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "无权限激活该账号");
            }
            List<Long> allowed = getAllowedRegionIds(operator.getRegionId());
            if (allowed == null || !allowed.contains(target.getRegionId())) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "无权限激活该账号");
            }
            target.setStatus(USER_STATUS_ACTIVE);
            target.setActivatedBy(operatorId);
            target.setActivatedAt(LocalDateTime.now());
            sysUserMapper.updateById(target);
            return;
        }
        throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "无权限操作");
    }

    @Override
    public void requestPasswordReset(PasswordResetRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "参数错误");
        }
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        if (email.isBlank() && username.isBlank()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "请输入用户名或邮箱");
        }

        if (!email.isBlank()) {
            requestPasswordResetByEmail(email);
            return;
        }

        SysUser user = sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getDeleted, 0)
        );
        if (user == null) {
            return;
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "该账号未绑定邮箱，请使用原密码重置或联系管理员进行密码重置");
        }
        requestPasswordResetByEmail(user.getEmail().trim().toLowerCase());
    }

    private void requestPasswordResetByEmail(String email) {
        String sentKey = PWD_RESET_SENT_KEY_PREFIX + email;
        Boolean locked = stringRedisTemplate.hasKey(sentKey);
        if (Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "发送过于频繁，请稍后再试");
        }

        SysUser user = sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, email)
                .eq(SysUser::getDeleted, 0)
        );
        if (user == null) {
            stringRedisTemplate.opsForValue().set(sentKey, "1", 60, TimeUnit.SECONDS);
            return;
        }

        String code = String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));
        String key = PWD_RESET_CODE_KEY_PREFIX + email;
        stringRedisTemplate.opsForValue().set(key, code, 1, TimeUnit.DAYS);
        stringRedisTemplate.opsForValue().set(sentKey, "1", 60, TimeUnit.SECONDS);

        String resetUrl = serverConfig.getFrontendBaseUrl()
            + "/reset-password?email="
            + URLEncoder.encode(email, StandardCharsets.UTF_8)
            + "&code="
            + URLEncoder.encode(code, StandardCharsets.UTF_8);

        SimpleMailMessage msg = new SimpleMailMessage();
        String from = env.getProperty("spring.mail.username");
        if (from != null && !from.isBlank()) {
            msg.setFrom(from.trim());
        }
        msg.setTo(email);
        msg.setSubject("排队叫号系统 - 密码重置验证码");
        msg.setText("你正在重置「排队叫号系统」账号密码。\n\n"
            + "验证码：" + code + "\n"
            + "有效期：24小时\n\n"
            + "点击链接重置密码：\n" + resetUrl + "\n\n"
            + "如非本人操作，请忽略此邮件。");
        try {
            mailSender.send(msg);
        } catch (Exception e) {
            stringRedisTemplate.delete(key);
            e.printStackTrace();
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "邮件发送失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "未登录");
        }
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "请输入原密码");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "请输入新密码");
        }
        if (newPassword.length() < 6) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "密码至少6位");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "账号不存在");
        }
        if (!new BCryptPasswordEncoder().matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "原密码不正确");
        }
        user.setPassword(PasswordUtil.encodeBCrypt(newPassword));
        sysUserMapper.updateById(user);
    }

    @Override
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "参数错误");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "请输入邮箱");
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "请输入验证码");
        }
        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "请输入新密码");
        }
        String newPassword = request.getNewPassword();
        if (newPassword.length() < 6) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "密码至少6位");
        }
        String email = request.getEmail().trim().toLowerCase();
        String key = PWD_RESET_CODE_KEY_PREFIX + email;
        String expected = stringRedisTemplate.opsForValue().get(key);
        if (expected == null || expected.isBlank()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "验证码已过期或无效");
        }
        if (!expected.equals(request.getCode().trim())) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "验证码错误");
        }
        SysUser user = sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, email)
                .eq(SysUser::getDeleted, 0)
        );
        if (user == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "账号不存在");
        }
        user.setPassword(PasswordUtil.encodeBCrypt(newPassword));
        sysUserMapper.updateById(user);
        stringRedisTemplate.delete(key);
    }

    private String resolveRegionCode(Long regionId) {
        if (regionId == null) {
            return null;
        }
        Region region = regionMapper.selectById(regionId);
        if (region == null) {
            return null;
        }
        return region.getRegionCode();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "用户不存在");
        }
        // 物理删除（直接删除，不做软删除）
        sysUserMapper.physicalDeleteById(id);
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


    private List<Long> intersectIds(List<Long> left, List<Long> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> rightSet = new HashSet<>(right);
        return left.stream().filter(rightSet::contains).collect(Collectors.toList());
    }

    private List<Long> getAssignableMenuIds(SysUser operator, SysUser target) {
        List<Long> targetRoleMenuIds = sysPermissionMapper.selectMenuIdsByRole(target.getRole());
        List<Long> operatorRoleMenuIds = sysPermissionMapper.selectMenuIdsByRole(operator.getRole());
        return intersectIds(operatorRoleMenuIds, targetRoleMenuIds);
    }

    private List<Long> getAssignableButtonIds(SysUser operator, SysUser target) {
        List<Long> targetRoleButtonIds = sysPermissionMapper.selectButtonIdsByRole(target.getRole());
        List<Long> operatorRoleButtonIds = sysPermissionMapper.selectButtonIdsByRole(operator.getRole());
        return intersectIds(operatorRoleButtonIds, targetRoleButtonIds);
    }

    /**
     * 校验操作者的权限是否合法
     */
    private void validatePermission(SysUser operator, SysUser target, UserPermissionDTO dto) {
        if (operator == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "请先登录");
        }

        if ("REGION_ADMIN".equals(operator.getRole()) && !"WINDOW_OPERATOR".equals(target.getRole())) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "只能为窗口操作员分配权限");
        }

        if (!"SUPER_ADMIN".equals(operator.getRole()) && !"REGION_ADMIN".equals(operator.getRole())) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "无权限操作");
        }

        List<Long> assignableMenuIds = getAssignableMenuIds(operator, target);
        List<Long> assignableButtonIds = getAssignableButtonIds(operator, target);
        if (dto.getMenuIds() != null) {
            for (Long menuId : dto.getMenuIds()) {
                if (!assignableMenuIds.contains(menuId)) {
                    throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "无权分配该菜单权限");
                }
            }
        }
        if (dto.getButtonIds() != null) {
            for (Long buttonId : dto.getButtonIds()) {
                if (!assignableButtonIds.contains(buttonId)) {
                    throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "无权分配该按钮权限");
                }
            }
        }
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
        boolean menuConfigured = menuIds != null && !menuIds.isEmpty();
        boolean buttonConfigured = buttonIds != null && !buttonIds.isEmpty();
        dto.setMenuIds(menuConfigured
            ? menuIds.stream().filter(id -> id != 0L).collect(java.util.stream.Collectors.toList())
            : null);
        dto.setButtonIds(buttonConfigured
            ? buttonIds.stream().filter(id -> id != 0L).collect(java.util.stream.Collectors.toList())
            : null);
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

    @Override
    public List<SysMenu> getAvailableMenusForTargetUser(Long operatorId, Long targetUserId) {
        SysUser operator = sysUserMapper.selectById(operatorId);
        SysUser target = sysUserMapper.selectById(targetUserId);
        if (operator == null || target == null) {
            return new ArrayList<>();
        }
        if ("REGION_ADMIN".equals(operator.getRole()) && !"WINDOW_OPERATOR".equals(target.getRole())) {
            return new ArrayList<>();
        }
        if (!"SUPER_ADMIN".equals(operator.getRole()) && !"REGION_ADMIN".equals(operator.getRole())) {
            return new ArrayList<>();
        }
        List<Long> menuIds = getAssignableMenuIds(operator, target);
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
    public List<SysButton> getAvailableButtonsForTargetUser(Long operatorId, Long targetUserId) {
        SysUser operator = sysUserMapper.selectById(operatorId);
        SysUser target = sysUserMapper.selectById(targetUserId);
        if (operator == null || target == null) {
            return new ArrayList<>();
        }
        if ("REGION_ADMIN".equals(operator.getRole()) && !"WINDOW_OPERATOR".equals(target.getRole())) {
            return new ArrayList<>();
        }
        if (!"SUPER_ADMIN".equals(operator.getRole()) && !"REGION_ADMIN".equals(operator.getRole())) {
            return new ArrayList<>();
        }
        List<Long> buttonIds = getAssignableButtonIds(operator, target);
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

    @Override
    public List<Long> getUserRegionScopes(Long operatorId, Long targetUserId) {
        SysUser operator = operatorId == null ? null : sysUserMapper.selectById(operatorId);
        SysUser target = targetUserId == null ? null : sysUserMapper.selectById(targetUserId);
        if (operator == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "请先登录");
        }
        if (target == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "用户不存在");
        }
        validateRegionScopePermission(operator, target);
        List<Long> scopes = sysUserMapper.selectRegionScopeIds(targetUserId);
        return scopes == null ? new ArrayList<>() : scopes;
    }

    @Override
    @Transactional
    public void setUserRegionScopes(Long operatorId, Long targetUserId, List<Long> regionIds) {
        SysUser operator = operatorId == null ? null : sysUserMapper.selectById(operatorId);
        SysUser target = targetUserId == null ? null : sysUserMapper.selectById(targetUserId);
        if (operator == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "请先登录");
        }
        if (target == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "用户不存在");
        }
        validateRegionScopePermission(operator, target);

        List<Long> ids = regionIds == null
            ? new ArrayList<>()
            : regionIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());

        if ("REGION_ADMIN".equals(operator.getRole())) {
            List<Long> allowed = getAllowedRegionIds(operator.getRegionId());
            Set<Long> allowedSet = new HashSet<>(allowed == null ? new ArrayList<>() : allowed);
            for (Long rid : ids) {
                if (!allowedSet.contains(rid)) {
                    throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "无权分配该区域范围");
                }
            }
        }

        sysUserMapper.deleteRegionScopes(targetUserId);
        for (Long rid : ids) {
            sysUserMapper.insertRegionScope(targetUserId, rid);
        }
    }

    private void validateRegionScopePermission(SysUser operator, SysUser target) {
        if (!"SUPER_ADMIN".equals(operator.getRole()) && !"REGION_ADMIN".equals(operator.getRole())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "无权限操作");
        }
        if ("REGION_ADMIN".equals(operator.getRole()) && !"WINDOW_OPERATOR".equals(target.getRole())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "只能为窗口操作员配置区域范围");
        }
    }

    @Override
    public byte[] generateImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet templateSheet = workbook.createSheet("用户导入模板");
            Row headerRow = templateSheet.createRow(0);
            for (int i = 0; i < IMPORT_HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(IMPORT_HEADERS[i]);
                templateSheet.setColumnWidth(i, switch (i) {
                    case 0 -> 18 * 256;
                    case 1 -> 18 * 256;
                    case 2 -> 25 * 256;
                    case 3 -> 18 * 256;
                    case 4 -> 18 * 256;
                    case 5 -> 18 * 256;
                    default -> 15 * 256;
                });
            }

            Sheet exampleSheet = workbook.createSheet("示例数据");
            Row exampleHeaderRow = exampleSheet.createRow(0);
            for (int i = 0; i < IMPORT_HEADERS.length; i++) {
                exampleHeaderRow.createCell(i).setCellValue(IMPORT_HEADERS[i]);
                exampleSheet.setColumnWidth(i, templateSheet.getColumnWidth(i));
            }
            String[][] exampleRows = {
                    {"zhangsan", "张三", "zhangsan@example.com", "WINDOW_OPERATOR", "440300", "Pass123456", "待激活"},
                    {"lisi", "李四", "lisi@example.com", "WINDOW_OPERATOR", "440305", "", "已激活"},
                    {"wangwu", "王五", "", "REGION_ADMIN", "440300", "Admin@2024", "已激活"}
            };
            for (int i = 0; i < exampleRows.length; i++) {
                Row row = exampleSheet.createRow(i + 1);
                for (int j = 0; j < exampleRows[i].length; j++) {
                    row.createCell(j).setCellValue(exampleRows[i][j]);
                }
            }

            Sheet roleSheet = workbook.createSheet("角色参照");
            String[][] roleReference = {
                    {"角色编码", "角色名称", "说明"},
                    {"SUPER_ADMIN", "超级管理员", "拥有系统所有权限"},
                    {"REGION_ADMIN", "区域管理员", "管理指定区域的用户和窗口"},
                    {"WINDOW_OPERATOR", "窗口操作员", "操作窗口叫号和服务"}
            };
            for (int i = 0; i < roleReference.length; i++) {
                Row row = roleSheet.createRow(i);
                for (int j = 0; j < roleReference[i].length; j++) {
                    row.createCell(j).setCellValue(roleReference[i][j]);
                }
            }
            roleSheet.setColumnWidth(0, 20 * 256);
            roleSheet.setColumnWidth(1, 18 * 256);
            roleSheet.setColumnWidth(2, 35 * 256);

            Sheet instructionSheet = workbook.createSheet("填写说明");
            String[][] instructions = {
                    {"字段", "说明"},
                    {"用户名", "必填,长度建议不超过 50 字,必须唯一."},
                    {"姓名", "必填,长度建议不超过 50 字."},
                    {"邮箱", "选填,必须唯一,用于密码重置."},
                    {"角色", "必填,填写角色编码,如 WINDOW_OPERATOR,详见【角色参照】工作表."},
                    {"区域代码", "必填,必须是系统中已存在的区域代码,如 440300."},
                    {"初始密码", "选填,留空时系统自动生成随机密码.密码至少6位."},
                    {"状态", "选填,支持 待激活/已激活/已禁用,留空默认为待激活."},
                    {"权限规则", "超级管理员可导入所有区域用户;区域管理员只能导入其管理区域范围内的用户."},
                    {"角色限制", "区域管理员不能导入 REGION_ADMIN 角色的用户."},
                    {"示例1", "zhangsan | 张三 | zhangsan@example.com | WINDOW_OPERATOR | 440300 | Pass123456 | 待激活"},
                    {"示例2", "lisi | 李四 | lisi@example.com | WINDOW_OPERATOR | 440305 |  | 已激活"},
                    {"示例3", "wangwu | 王五 |  | REGION_ADMIN | 440300 | Admin@2024 | 已激活"}
            };
            for (int i = 0; i < instructions.length; i++) {
                Row row = instructionSheet.createRow(i);
                row.createCell(0).setCellValue(instructions[i][0]);
                row.createCell(1).setCellValue(instructions[i][1]);
            }
            instructionSheet.setColumnWidth(0, 18 * 256);
            instructionSheet.setColumnWidth(1, 80 * 256);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "生成导入模板失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserImportResult importUsers(MultipartFile file, SysUser currentUser) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请先选择要导入的 Excel 文件");
        }

        if (currentUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "请先登录");
        }

        if (!"SUPER_ADMIN".equals(currentUser.getRole()) && !"REGION_ADMIN".equals(currentUser.getRole())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "仅管理员角色可以批量导入用户");
        }

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BusinessException(400, "导入文件缺少数据工作表");
            }

            Sheet sheet = workbook.getSheet("用户导入模板");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            DataFormatter formatter = new DataFormatter();
            validateImportHeader(sheet.getRow(0), formatter);
            List<UserImportRow> importRows = parseImportRows(sheet, formatter);
            if (importRows.isEmpty()) {
                throw new BusinessException(400, "导入文件中没有可导入的数据");
            }

            List<Long> allowedRegionIds = getAllowedRegionIdsForImport(currentUser);
            Map<String, SysUser> existingUsersByUsername = loadExistingUsersByUsername();
            Map<String, SysUser> existingUsersByEmail = loadExistingUsersByEmail();

            List<UserImportResult.UserImportDetail> details = new ArrayList<>();
            int importedCount = 0;

            for (UserImportRow importRow : importRows) {
                validateImportRow(importRow, currentUser, allowedRegionIds, existingUsersByUsername, existingUsersByEmail);

                String password = StringUtils.hasText(importRow.password())
                        ? importRow.password()
                        : com.queue.util.PasswordUtil.generateRandomPassword(10);

                SysUserDTO dto = new SysUserDTO();
                dto.setUsername(importRow.username());
                dto.setPassword(password);
                dto.setName(importRow.name());
                dto.setEmail(StringUtils.hasText(importRow.email()) ? importRow.email() : null);
                dto.setRole(importRow.role());
                dto.setRegionId(importRow.regionId());
                dto.setStatus(importRow.status());

                SysUser created = create(dto);
                existingUsersByUsername.put(created.getUsername(), created);
                if (created.getEmail() != null) {
                    existingUsersByEmail.put(created.getEmail(), created);
                }

                if (!StringUtils.hasText(importRow.password())) {
                    details.add(new UserImportResult.UserImportDetail(created.getUsername(), password));
                }

                importedCount++;
            }

            return new UserImportResult(importedCount, details);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "读取 Excel 失败,请使用系统模板并上传 .xlsx 或 .xls 文件");
        }
    }

    private void validateImportHeader(Row headerRow, DataFormatter formatter) {
        if (headerRow == null) {
            throw new BusinessException(400, "导入文件缺少表头,请先下载模板");
        }
        for (int i = 0; i < IMPORT_HEADERS.length; i++) {
            String actual = formatter.formatCellValue(headerRow.getCell(i)).trim();
            if (!IMPORT_HEADERS[i].equals(actual)) {
                throw new BusinessException(400, "模板表头不正确,请先下载最新模板");
            }
        }
    }

    private List<UserImportRow> parseImportRows(Sheet sheet, DataFormatter formatter) {
        List<UserImportRow> rows = new ArrayList<>();
        Set<String> batchUsernames = new HashSet<>();

        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            String username = formatter.formatCellValue(row.getCell(0)).trim();
            String name = formatter.formatCellValue(row.getCell(1)).trim();
            String email = formatter.formatCellValue(row.getCell(2)).trim();
            String role = formatter.formatCellValue(row.getCell(3)).trim();
            String regionCode = formatter.formatCellValue(row.getCell(4)).trim();
            String password = formatter.formatCellValue(row.getCell(5)).trim();
            String statusText = formatter.formatCellValue(row.getCell(6)).trim();

            if (!StringUtils.hasText(username)
                    && !StringUtils.hasText(name)
                    && !StringUtils.hasText(email)
                    && !StringUtils.hasText(role)
                    && !StringUtils.hasText(regionCode)
                    && !StringUtils.hasText(password)
                    && !StringUtils.hasText(statusText)) {
                continue;
            }

            int displayRowNum = rowIndex + 1;
            if (!StringUtils.hasText(username)) {
                throw new BusinessException(400, "第 " + displayRowNum + " 行缺少用户名");
            }
            if (!StringUtils.hasText(name)) {
                throw new BusinessException(400, "第 " + displayRowNum + " 行缺少姓名");
            }
            if (!StringUtils.hasText(role)) {
                throw new BusinessException(400, "第 " + displayRowNum + " 行缺少角色");
            }
            if (!StringUtils.hasText(regionCode)) {
                throw new BusinessException(400, "第 " + displayRowNum + " 行缺少区域代码");
            }
            if (username.length() > 50) {
                throw new BusinessException(400, "第 " + displayRowNum + " 行用户名长度不能超过 50");
            }
            if (name.length() > 50) {
                throw new BusinessException(400, "第 " + displayRowNum + " 行姓名长度不能超过 50");
            }
            if (!batchUsernames.add(username)) {
                throw new BusinessException(400, "第 " + displayRowNum + " 行用户名重复: " + username);
            }
            if (StringUtils.hasText(password) && password.length() < 6) {
                throw new BusinessException(400, "第 " + displayRowNum + " 行密码至少6位");
            }

            Long regionId = findRegionIdByCode(regionCode, displayRowNum);
            String normalizedRole = normalizeRole(role, displayRowNum);
            Integer status = normalizeStatus(statusText, displayRowNum);

            rows.add(new UserImportRow(
                    displayRowNum,
                    username,
                    name,
                    StringUtils.hasText(email) ? email.trim().toLowerCase() : null,
                    normalizedRole,
                    regionId,
                    regionCode,
                    StringUtils.hasText(password) ? password : null,
                    status
            ));
        }

        return rows;
    }

    private Long findRegionIdByCode(String regionCode, int displayRowNum) {
        Region region = regionMapper.selectOne(new LambdaQueryWrapper<Region>()
                .eq(Region::getRegionCode, regionCode)
                .eq(Region::getDeleted, 0));
        if (region == null) {
            throw new BusinessException(400, "第 " + displayRowNum + " 行区域代码不存在: " + regionCode);
        }
        return region.getId();
    }

    private Long parseRegionId(String regionIdText, int displayRowNum) {
        try {
            return Long.parseLong(regionIdText);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "第 " + displayRowNum + " 行区域ID必须为数字");
        }
    }

    private String normalizeRole(String roleText, int displayRowNum) {
        if (!StringUtils.hasText(roleText)) {
            throw new BusinessException(400, "第 " + displayRowNum + " 行缺少角色");
        }
        String normalized = roleText.trim().toUpperCase();
        return switch (normalized) {
            case "SUPER_ADMIN", "超级管理员" -> "SUPER_ADMIN";
            case "REGION_ADMIN", "区域管理员" -> "REGION_ADMIN";
            case "WINDOW_OPERATOR", "窗口操作员" -> "WINDOW_OPERATOR";
            default -> throw new BusinessException(400, "第 " + displayRowNum + " 行角色无效,请填写 SUPER_ADMIN/REGION_ADMIN/WINDOW_OPERATOR 或对应中文");
        };
    }

    private Integer normalizeStatus(String statusText, int displayRowNum) {
        if (!StringUtils.hasText(statusText)) {
            return USER_STATUS_PENDING;
        }
        String normalized = statusText.trim();
        return switch (normalized) {
            case "待激活", "0" -> USER_STATUS_PENDING;
            case "已激活", "1" -> USER_STATUS_ACTIVE;
            case "已禁用", "2" -> USER_STATUS_DISABLED;
            default -> throw new BusinessException(400, "第 " + displayRowNum + " 行状态无效,请填写 待激活/已激活/已禁用");
        };
    }

    private List<Long> getAllowedRegionIdsForImport(SysUser currentUser) {
        if ("SUPER_ADMIN".equals(currentUser.getRole())) {
            return null;
        }
        return getAllowedRegionIds(currentUser.getRegionId());
    }

    private Map<String, SysUser> loadExistingUsersByUsername() {
        List<SysUser> allUsers = listAll();
        Map<String, SysUser> map = new HashMap<>();
        for (SysUser user : allUsers) {
            if (StringUtils.hasText(user.getUsername())) {
                map.put(user.getUsername().trim(), user);
            }
        }
        return map;
    }

    private Map<String, SysUser> loadExistingUsersByEmail() {
        List<SysUser> allUsers = listAll();
        Map<String, SysUser> map = new HashMap<>();
        for (SysUser user : allUsers) {
            if (StringUtils.hasText(user.getEmail())) {
                map.put(user.getEmail().trim().toLowerCase(), user);
            }
        }
        return map;
    }

    private void validateImportRow(UserImportRow importRow, SysUser currentUser, List<Long> allowedRegionIds,
                                    Map<String, SysUser> existingUsersByUsername, Map<String, SysUser> existingUsersByEmail) {
        if (existingUsersByUsername.containsKey(importRow.username())) {
            throw new BusinessException(400, "第 " + importRow.rowNum() + " 行用户名已存在: " + importRow.username());
        }

        if (importRow.email() != null && existingUsersByEmail.containsKey(importRow.email())) {
            throw new BusinessException(400, "第 " + importRow.rowNum() + " 行邮箱已存在: " + importRow.email());
        }

        Region region = regionMapper.selectById(importRow.regionId());
        if (region == null) {
            throw new BusinessException(400, "第 " + importRow.rowNum() + " 行区域ID不存在: " + importRow.regionId());
        }

        if ("REGION_ADMIN".equals(currentUser.getRole())) {
            if ("REGION_ADMIN".equals(importRow.role())) {
                throw new BusinessException(403, "第 " + importRow.rowNum() + " 行区域管理员不能导入 REGION_ADMIN 角色");
            }
            if (allowedRegionIds != null && !allowedRegionIds.contains(importRow.regionId())) {
                throw new BusinessException(403, "第 " + importRow.rowNum() + " 行无权限导入该区域的用户: " + region.getRegionName());
            }
        }
    }

    private record UserImportRow(
            int rowNum,
            String username,
            String name,
            String email,
            String role,
            Long regionId,
            String regionCode,
            String password,
            Integer status
    ) {
    }
}
