package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysRoleMenuMapper extends BaseMapper<Object> {

    @Insert("INSERT IGNORE INTO sys_role_menu (role, menu_id) VALUES (#{role}, #{menuId})")
    int insertRoleMenu(String role, Long menuId);
}
