package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.queue.entity.Region;
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
}
