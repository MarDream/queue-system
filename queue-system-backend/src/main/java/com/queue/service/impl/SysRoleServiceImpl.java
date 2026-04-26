package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.queue.common.BusinessException;
import com.queue.common.ResultCode;
import com.queue.dto.SysRoleDTO;
import com.queue.entity.SysRole;
import com.queue.entity.SysUser;
import com.queue.enums.RoleType;
import com.queue.mapper.SysRoleMapper;
import com.queue.mapper.SysUserMapper;
import com.queue.service.SysRoleService;
import com.queue.util.PinyinUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysUserMapper sysUserMapper;
    private static final int CODE_MAX_LEN = 20;

    @Override
    public List<SysRole> listAll() {
        return sysRoleMapper.selectList(
            new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getDeleted, 0)
                .orderByAsc(SysRole::getSortOrder)
        );
    }

    @Override
    public SysRole getById(Long id) {
        return sysRoleMapper.selectById(id);
    }

    @Override
    public SysRole getByCode(String code) {
        return sysRoleMapper.selectOne(
            new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getDeleted, 0)
                .eq(SysRole::getCode, code)
        );
    }

    @Override
    @Transactional
    public SysRole create(SysRoleDTO dto) {
        String code = normalizeCode(dto.getCode());
        if (!StringUtils.hasText(code)) {
            code = generateUniqueRoleCode(dto.getName());
        } else if (existsByCode(code)) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "角色编码已存在");
        }

        SysRole role = new SysRole();
        role.setCode(code);
        role.setName(dto.getName());
        role.setDescription(dto.getDescription());
        role.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        role.setType(RoleType.CUSTOM.name());
        sysRoleMapper.insert(role);
        return role;
    }

    @Override
    @Transactional
    public SysRole update(SysRoleDTO dto) {
        SysRole role = sysRoleMapper.selectById(dto.getId());
        if (role == null || role.getDeleted() == 1) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "角色不存在");
        }

        if (dto.getName() != null) {
            role.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            role.setDescription(dto.getDescription());
        }
        if (dto.getSortOrder() != null) {
            role.setSortOrder(dto.getSortOrder());
        }

        sysRoleMapper.updateById(role);
        return role;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null || role.getDeleted() == 1) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "角色不存在");
        }
        if (RoleType.SYSTEM.name().equals(role.getType())) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "内置角色不可删除");
        }
        Long usingCount = sysUserMapper.selectCount(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .eq(SysUser::getRole, role.getCode())
        );
        if (usingCount != null && usingCount > 0) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "有用户正在使用该角色，不能删除角色");
        }

        sysRoleMapper.deleteById(id);
    }

    private boolean existsByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        Long count = sysRoleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getDeleted, 0)
                        .eq(SysRole::getCode, code)
        );
        return count != null && count > 0;
    }

    private String normalizeCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String generateUniqueRoleCode(String roleName) {
        String base = buildRoleCodeBase(roleName);
        if (!existsByCode(base)) {
            return base;
        }

        for (int i = 2; i < 1000; i++) {
            String candidate = withNumericSuffix(base, i);
            if (!existsByCode(candidate)) {
                return candidate;
            }
        }

        throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "系统生成角色编码失败，请更换角色名称后重试");
    }

    private String buildRoleCodeBase(String roleName) {
        String name = roleName == null ? "" : roleName.trim();
        String mapped = mapKnownRoleNames(name);
        if (mapped != null) {
            return mapped;
        }

        String normalized = normalizeForCode(name);
        if (StringUtils.hasText(normalized) && containsAsciiLetter(name)) {
            return truncateCode(normalized);
        }

        String built = buildChineseAbbrevCode(name);
        if (StringUtils.hasText(built)) {
            return truncateCode(built);
        }

        if (StringUtils.hasText(normalized)) {
            return truncateCode(normalized);
        }

        return "ROLE";
    }

    private String withNumericSuffix(String base, int number) {
        String suffix = "_" + number;
        int allowedLen = CODE_MAX_LEN - suffix.length();
        String head = base.length() > allowedLen ? base.substring(0, allowedLen) : base;
        if (head.endsWith("_")) {
            head = head.substring(0, head.length() - 1);
        }
        return head + suffix;
    }

    private String normalizeForCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();

        String upper = trimmed.toUpperCase(Locale.ROOT);
        String cleaned = upper
                .replaceAll("[\\s\\-]+", "_")
                .replaceAll("[^A-Z0-9_]+", "_")
                .replaceAll("_+", "_");
        cleaned = cleaned.replaceAll("^_+|_+$", "");
        return cleaned;
    }

    private boolean containsAsciiLetter(String raw) {
        if (!StringUtils.hasText(raw)) return false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return true;
            }
        }
        return false;
    }

    private String buildChineseAbbrevCode(String roleName) {
        if (!StringUtils.hasText(roleName)) return null;
        String name = roleName.trim();

        Set<String> parts = new LinkedHashSet<>();
        addPartIfContains(parts, name, "超级", "SUPER");
        addPartIfContains(parts, name, "区域", "REGION");
        addPartIfContains(parts, name, "窗口", "WINDOW");
        addPartIfContains(parts, name, "业务", "BIZ");
        addPartIfContains(parts, name, "系统", "SYSTEM");
        addPartIfContains(parts, name, "用户", "USER");
        addPartIfContains(parts, name, "菜单", "MENU");
        addPartIfContains(parts, name, "财务", "FINANCE");
        addPartIfContains(parts, name, "客服", "SERVICE");
        addPartIfContains(parts, name, "运营", "OPS");

        String suffix = null;
        if (name.contains("管理员")) {
            suffix = "ADMIN";
        } else if (name.contains("操作员") || name.contains("坐席")) {
            suffix = "OPERATOR";
        } else if (name.contains("审核") || name.contains("审计")) {
            suffix = "AUDITOR";
        } else if (name.contains("主管") || name.contains("经理")) {
            suffix = "MANAGER";
        }

        String rest = name;
        String[] removeTokens = {
                "超级", "区域", "窗口", "业务", "系统", "用户", "菜单", "财务", "客服", "运营",
                "管理员", "管理", "操作员", "坐席", "审核员", "审核", "审计", "主管", "经理",
                "专员", "人员", "角色", "权限"
        };
        for (String token : removeTokens) {
            rest = rest.replace(token, "");
        }
        rest = rest.replaceAll("[\\s\\-_/]+", "");

        String initials = normalizeForCode(PinyinUtil.getPinyinInitials(rest));
        if (StringUtils.hasText(initials)) {
            if (initials.length() > 8) {
                initials = initials.substring(0, 8);
            }
            parts.add(initials);
        }

        if (suffix != null) {
            parts.add(suffix);
        }

        if (parts.isEmpty()) {
            String allInitials = normalizeForCode(PinyinUtil.getPinyinInitials(name));
            return StringUtils.hasText(allInitials) ? allInitials : null;
        }

        return normalizeForCode(String.join("_", parts));
    }

    private void addPartIfContains(Set<String> parts, String roleName, String keyword, String codePart) {
        if (roleName.contains(keyword)) {
            parts.add(codePart);
        }
    }

    private String truncateCode(String code) {
        if (!StringUtils.hasText(code)) return code;
        return code.length() > CODE_MAX_LEN ? code.substring(0, CODE_MAX_LEN) : code;
    }

    private String mapKnownRoleNames(String roleName) {
        String name = roleName.trim();
        if ("超级管理员".equals(name) || (name.contains("超级") && name.contains("管理员"))) return "SUPER_ADMIN";
        if ("区域管理员".equals(name) || (name.contains("区域") && name.contains("管理员"))) return "REGION_ADMIN";
        if ("窗口操作员".equals(name) || (name.contains("窗口") && (name.contains("操作员") || name.contains("坐席")))) return "WINDOW_OPERATOR";
        return null;
    }
}
