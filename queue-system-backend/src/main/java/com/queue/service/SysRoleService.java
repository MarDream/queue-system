package com.queue.service;

import com.queue.dto.SysRoleDTO;
import com.queue.entity.SysRole;

import java.util.List;

public interface SysRoleService {
    List<SysRole> listAll();
    SysRole getById(Long id);
    SysRole getByCode(String code);
    SysRole create(SysRoleDTO dto);
    SysRole update(SysRoleDTO dto);
    void delete(Long id);
}
