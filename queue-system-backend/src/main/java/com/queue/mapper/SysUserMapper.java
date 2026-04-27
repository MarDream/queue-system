package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.queue.entity.SysUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT u.* FROM sys_user u " +
            "INNER JOIN region r ON u.region_id = r.id " +
            "WHERE u.deleted = 0 AND u.status = 1 AND u.role = #{role} " +
            "AND r.id = #{regionId} " +
            "ORDER BY u.name")
    List<SysUser> selectByRegionIdAndRole(@Param("regionId") Long regionId, @Param("role") String role);

    /**
     * 物理删除（不经过逻辑删除）
     */
    @Delete("DELETE FROM sys_user WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    @Select("SELECT region_id FROM sys_user_region_scope WHERE user_id = #{userId}")
    List<Long> selectRegionScopeIds(@Param("userId") Long userId);

    @Delete("DELETE FROM sys_user_region_scope WHERE user_id = #{userId}")
    int deleteRegionScopes(@Param("userId") Long userId);

    @Insert("INSERT INTO sys_user_region_scope(user_id, region_id) VALUES(#{userId}, #{regionId})")
    int insertRegionScope(@Param("userId") Long userId, @Param("regionId") Long regionId);
}
