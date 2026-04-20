package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysRoleButtonMapper extends BaseMapper<Object> {

    @Select("SELECT button_id FROM sys_role_button WHERE role = #{role}")
    List<Long> selectButtonIdsByRole(String role);

    @Insert("INSERT IGNORE INTO sys_role_button (role, button_id) VALUES (#{role}, #{buttonId})")
    int insertRoleButton(String role, Long buttonId);

    @Delete("DELETE FROM sys_role_button WHERE role = #{role}")
    int deleteByRole(String role);
}