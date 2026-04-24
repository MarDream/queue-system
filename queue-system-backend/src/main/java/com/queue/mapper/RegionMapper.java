package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.queue.entity.Region;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RegionMapper extends BaseMapper<Region> {

    @Select("WITH RECURSIVE region_tree AS (" +
            "  SELECT id, parent_id FROM region WHERE id = #{regionId} AND deleted = 0 " +
            "  UNION ALL " +
            "  SELECT r.id, r.parent_id FROM region r " +
            "  INNER JOIN region_tree rt ON r.parent_id = rt.id WHERE r.deleted = 0" +
            ") SELECT DISTINCT id FROM region_tree")
    List<Long> selectDescendantIds(@Param("regionId") Long regionId);

    /**
     * 获取区域的完整名称路径（父区域名称-子区域名称）
     * 例如：深圳市-南山区
     */
    @Select("WITH RECURSIVE region_path AS (" +
            "  SELECT id, region_name, parent_id, CAST(region_name AS CHAR(500)) AS full_path " +
            "  FROM region WHERE id = #{regionId} AND deleted = 0 " +
            "  UNION ALL " +
            "  SELECT r.id, r.region_name, r.parent_id, CONCAT(rp.full_path, '-', r.region_name) " +
            "  FROM region r INNER JOIN region_path rp ON r.id = rp.parent_id " +
            "  WHERE r.deleted = 0" +
            ") SELECT full_path FROM region_path ORDER BY LENGTH(full_path) ASC LIMIT 1")
    String selectFullRegionPath(@Param("regionId") Long regionId);

    /**
     * 物理删除（不经过逻辑删除）
     */
    @Delete("DELETE FROM region WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
