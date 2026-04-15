package com.queue.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysPermissionMapper {

    @Select("SELECT menu_id FROM sys_role_menu WHERE role = #{role}")
    List<Long> selectMenuIdsByRole(String role);

    @Select("SELECT button_id FROM sys_role_button WHERE role = #{role}")
    List<Long> selectButtonIdsByRole(String role);

    @Insert("INSERT IGNORE INTO sys_role_button (role, button_id) VALUES (#{role}, #{buttonId})")
    int insertRoleButton(String role, Long buttonId);
}
