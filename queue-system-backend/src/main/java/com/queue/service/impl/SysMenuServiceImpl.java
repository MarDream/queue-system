package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.queue.common.BusinessException;
import com.queue.common.ResultCode;
import com.queue.dto.UserMenuSortDTO;
import com.queue.entity.SysMenu;
import com.queue.entity.SysUserMenuSort;
import com.queue.mapper.SysMenuMapper;
import com.queue.mapper.SysRoleMenuMapper;
import com.queue.mapper.SysUserMenuSortMapper;
import com.queue.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl implements SysMenuService {

    private final SysMenuMapper sysMenuMapper;
    private final SysUserMenuSortMapper sysUserMenuSortMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;

    @Override
    public List<SysMenu> listAll() {
        return sysMenuMapper.selectList(
            new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getDeleted, 0)
                .orderByAsc(SysMenu::getSortOrder)
        );
    }

    @Override
    public List<SysMenu> listByUserId(Long userId) {
        return listAll();
    }

    @Override
    @Transactional
    public void updateSortForUser(UserMenuSortDTO dto) {
        // 删除旧排序
        sysUserMenuSortMapper.delete(
            new LambdaQueryWrapper<SysUserMenuSort>()
                .eq(SysUserMenuSort::getUserId, dto.getUserId())
        );
        // 插入新排序
        if (dto.getMenuIds() != null) {
            for (int i = 0; i < dto.getMenuIds().size(); i++) {
                SysUserMenuSort sort = new SysUserMenuSort();
                sort.setUserId(dto.getUserId());
                sort.setMenuId(dto.getMenuIds().get(i));
                sort.setSortOrder(i);
                sysUserMenuSortMapper.insert(sort);
            }
        }
    }

    @Override
    @Transactional
    public void renameMenu(Long menuId, String newName) {
        SysMenu menu = sysMenuMapper.selectById(menuId);
        if (menu == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "菜单不存在");
        }
        // 检查名称是否与其他菜单重复
        List<SysMenu> allMenus = sysMenuMapper.selectList(
            new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getDeleted, 0)
        );
        boolean duplicate = allMenus.stream()
            .anyMatch(m -> !m.getId().equals(menuId) && m.getName().equals(newName));
        if (duplicate) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "菜单名称已存在，不能与其他菜单或子菜单名称相同");
        }
        sysMenuMapper.update(null,
            new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SysMenu>()
                .eq("id", menuId)
                .set("name", newName)
        );
    }

    @Override
    @Transactional
    public void updateMenuParent(Long menuId, Long parentId) {
        SysMenu menu = sysMenuMapper.selectById(menuId);
        if (menu == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "菜单不存在");
        }
        // 不能把自己设为自己的子菜单
        if (menuId.equals(parentId)) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "不能将菜单设为自己的子菜单");
        }
        // 检查循环引用
        if (parentId != null) {
            if (wouldCreateCycle(menuId, parentId)) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "不能将菜单设为其子菜单的后代");
            }
            // 验证父菜单存在
            SysMenu parent = sysMenuMapper.selectById(parentId);
            if (parent == null || parent.getDeleted() == 1) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "父菜单不存在");
            }
        }
        // 使用 UpdateWrapper 确保 null 值也能更新
        sysMenuMapper.update(null,
            new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SysMenu>()
                .eq("id", menuId)
                .set("parent_id", parentId)
        );
    }

    @Override
    @Transactional
    public void createGroup(String name) {
        // 查询最大 sortOrder
        SysMenu maxSort = sysMenuMapper.selectOne(
            new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getDeleted, 0)
                .orderByDesc(SysMenu::getSortOrder)
                .last("LIMIT 1")
        );
        int nextSort = (maxSort != null && maxSort.getSortOrder() != null) ? maxSort.getSortOrder() + 10 : 10;

        SysMenu group = new SysMenu();
        group.setName(name);
        group.setType("group");
        group.setPath("");
        group.setSortOrder(nextSort);
        group.setParentId(null);
        sysMenuMapper.insert(group);
    }

    @Override
    @Transactional
    public void deleteGroup(Long groupId) {
        SysMenu group = sysMenuMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "分组不存在");
        }
        if (!"group".equals(group.getType())) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "只能删除分组类型");
        }
        // 检查分组下是否有子菜单
        long childCount = sysMenuMapper.selectCount(
            new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, groupId)
                .eq(SysMenu::getDeleted, 0)
        );
        if (childCount > 0) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "分组下有子菜单，无法删除");
        }
        // 软删除分组
        group.setDeleted(1);
        sysMenuMapper.updateById(group);
    }

    /**
     * 检测将 menuId 的 parentId 设为 newParentId 是否会造成循环引用
     */
    private boolean wouldCreateCycle(Long menuId, Long newParentId) {
        // 从 newParentId 向上遍历，如果遇到 menuId 说明会形成循环
        Set<Long> visited = new HashSet<>();
        Long current = newParentId;
        while (current != null && !visited.contains(current)) {
            if (current.equals(menuId)) {
                return true;
            }
            visited.add(current);
            SysMenu menu = sysMenuMapper.selectById(current);
            if (menu == null) break;
            current = menu.getParentId();
        }
        return false;
    }

    @Override
    @Transactional
    public SysMenu createMenu(SysMenu menu) {
        // 检查名称是否重复
        long count = sysMenuMapper.selectCount(
            new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getDeleted, 0)
                .eq(SysMenu::getName, menu.getName())
        );
        if (count > 0) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "菜单名称已存在");
        }
        // 默认值
        if (menu.getSortOrder() == null) {
            SysMenu maxSort = sysMenuMapper.selectOne(
                new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getDeleted, 0)
                    .orderByDesc(SysMenu::getSortOrder)
                    .last("LIMIT 1")
            );
            menu.setSortOrder((maxSort != null && maxSort.getSortOrder() != null) ? maxSort.getSortOrder() + 10 : 10);
        }
        if (menu.getType() == null || menu.getType().isEmpty()) {
            menu.setType("menu");
        }
        sysMenuMapper.insert(menu);
        // 自动将新菜单关联到超级管理员角色
        sysRoleMenuMapper.insertRoleMenu("SUPER_ADMIN", menu.getId());
        return menu;
    }

    @Override
    @Transactional
    public SysMenu updateMenu(SysMenu menu) {
        SysMenu existing = sysMenuMapper.selectById(menu.getId());
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "菜单不存在");
        }
        // 如果修改了名称，检查是否重复
        if (menu.getName() != null && !menu.getName().equals(existing.getName())) {
            long count = sysMenuMapper.selectCount(
                new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getDeleted, 0)
                    .eq(SysMenu::getName, menu.getName())
                    .ne(SysMenu::getId, menu.getId())
            );
            if (count > 0) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "菜单名称已存在");
            }
        }
        // 如果修改了 parentId，检查循环引用
        if (menu.getParentId() != null && !menu.getParentId().equals(existing.getParentId())) {
            if (menu.getId().equals(menu.getParentId()) || wouldCreateCycle(menu.getId(), menu.getParentId())) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "无效的父菜单");
            }
        }
        sysMenuMapper.updateById(menu);
        return sysMenuMapper.selectById(menu.getId());
    }

    @Override
    @Transactional
    public void deleteMenu(Long id) {
        SysMenu menu = sysMenuMapper.selectById(id);
        if (menu == null || menu.getDeleted() == 1) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "菜单不存在");
        }
        // 分组类型需检查子项
        if ("group".equals(menu.getType())) {
            long childCount = sysMenuMapper.selectCount(
                new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getParentId, id)
                    .eq(SysMenu::getDeleted, 0)
            );
            if (childCount > 0) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "分组下有子菜单，无法删除");
            }
        }
        menu.setDeleted(1);
        sysMenuMapper.updateById(menu);
    }

    @Override
    public SysMenu getById(Long id) {
        return sysMenuMapper.selectById(id);
    }

    /**
     * 修复：确保指定菜单添加到超级管理员角色权限
     * 用于数据库初始化后补充缺失的权限关联
     */
    @Transactional
    public void ensureSuperAdminHasMenu(Long menuId) {
        List<Long> existingMenus = sysRoleMenuMapper.selectMenuIdsByRole("SUPER_ADMIN");
        if (existingMenus == null || !existingMenus.contains(menuId)) {
            sysRoleMenuMapper.insertRoleMenu("SUPER_ADMIN", menuId);
        }
    }

    /**
     * 修复：确保所有菜单都在超级管理员角色权限中
     * 用于修复历史遗留的权限缺失问题
     */
    @Transactional
    public void syncSuperAdminMenus() {
        List<SysMenu> allMenus = sysMenuMapper.selectList(
            new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getDeleted, 0)
        );
        List<Long> existingMenus = sysRoleMenuMapper.selectMenuIdsByRole("SUPER_ADMIN");
        if (existingMenus == null) {
            existingMenus = List.of();
        }
        for (SysMenu menu : allMenus) {
            if (!existingMenus.contains(menu.getId())) {
                sysRoleMenuMapper.insertRoleMenu("SUPER_ADMIN", menu.getId());
            }
        }
    }
}
