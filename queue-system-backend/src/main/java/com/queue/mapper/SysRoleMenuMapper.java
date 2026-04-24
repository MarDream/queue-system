package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysRoleMenuMapper extends BaseMapper<Object> {

    @Insert("INSERT IGNORE INTO sys_role_menu (role_id, role_code, menu_id) VALUES (#{roleId}, #{roleCode}, #{menuId})")
    int insertRoleMenu(@Param("roleId") Long roleId, @Param("roleCode") String roleCode, @Param("menuId") Long menuId);

    @Select("SELECT menu_id FROM sys_role_menu WHERE role_code = #{roleCode}")
    List<Long> selectMenuIdsByRole(@Param("roleCode") String roleCode);

    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_role_menu WHERE role_code = #{roleCode}")
    int deleteByRoleCode(@Param("roleCode") String roleCode);

    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
