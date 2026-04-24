package com.queue.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysPermissionMapper {

    @Select("SELECT menu_id FROM sys_role_menu WHERE role_code = #{roleCode}")
    List<Long> selectMenuIdsByRole(@Param("roleCode") String roleCode);

    @Select("SELECT button_id FROM sys_role_button WHERE role_code = #{roleCode}")
    List<Long> selectButtonIdsByRole(@Param("roleCode") String roleCode);

    @Insert("INSERT IGNORE INTO sys_role_button (role_id, role_code, button_id) VALUES (0, #{roleCode}, #{buttonId})")
    int insertRoleButton(@Param("roleCode") String roleCode, @Param("buttonId") Long buttonId);
}
