package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.queue.entity.SysUser;
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
}
