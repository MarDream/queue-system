package com.queue.service;

import com.queue.dto.UserMenuSortDTO;
import com.queue.entity.SysMenu;
import java.util.List;

public interface SysMenuService {
    List<SysMenu> listAll();
    List<SysMenu> listByUserId(Long userId);
    void updateSortForUser(UserMenuSortDTO dto);
    void renameMenu(Long menuId, String newName);
    void updateMenuParent(Long menuId, Long parentId);
    void createGroup(String name);
    void deleteGroup(Long groupId);

    // 通用菜单 CRUD
    SysMenu createMenu(SysMenu menu);
    SysMenu updateMenu(SysMenu menu);
    void deleteMenu(Long id);
    SysMenu getById(Long id);

    // 修复：同步超级管理员菜单权限
    void syncSuperAdminMenus();
}
