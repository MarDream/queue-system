package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysRoleButtonMapper extends BaseMapper<Object> {

    @Insert("INSERT IGNORE INTO sys_role_button (role_id, role_code, button_id) VALUES (#{roleId}, #{roleCode}, #{buttonId})")
    int insertRoleButton(@Param("roleId") Long roleId, @Param("roleCode") String roleCode, @Param("buttonId") Long buttonId);

    @Select("SELECT button_id FROM sys_role_button WHERE role_code = #{roleCode}")
    List<Long> selectButtonIdsByRole(@Param("roleCode") String roleCode);

    @Select("SELECT button_id FROM sys_role_button WHERE role_id = #{roleId}")
    List<Long> selectButtonIdsByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_role_button WHERE role_code = #{roleCode}")
    int deleteByRoleCode(@Param("roleCode") String roleCode);

    @Delete("DELETE FROM sys_role_button WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
