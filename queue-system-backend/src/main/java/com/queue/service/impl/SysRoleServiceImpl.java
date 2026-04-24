package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.queue.common.BusinessException;
import com.queue.common.ResultCode;
import com.queue.dto.SysRoleDTO;
import com.queue.entity.SysRole;
import com.queue.enums.RoleType;
import com.queue.mapper.SysRoleMapper;
import com.queue.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper sysRoleMapper;

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
        SysRole exist = sysRoleMapper.selectOne(
            new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getDeleted, 0)
                .eq(SysRole::getCode, dto.getCode())
        );
        if (exist != null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "角色编码已存在");
        }

        SysRole role = new SysRole();
        role.setCode(dto.getCode());
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

        sysRoleMapper.deleteById(id);
    }
}
