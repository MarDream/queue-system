package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysRoleMenuMapper extends BaseMapper<Object> {

    @Insert("INSERT IGNORE INTO sys_role_menu (role, menu_id) VALUES (#{role}, #{menuId})")
    int insertRoleMenu(String role, Long menuId);

    @Select("SELECT menu_id FROM sys_role_menu WHERE role = #{role}")
    List<Long> selectMenuIdsByRole(String role);

    @Delete("DELETE FROM sys_role_menu WHERE role = #{role}")
    int deleteByRole(String role);
}
