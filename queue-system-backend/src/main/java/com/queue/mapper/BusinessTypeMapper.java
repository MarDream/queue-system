package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.queue.entity.BusinessType;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BusinessTypeMapper extends BaseMapper<BusinessType> {

    /**
     * 物理删除（不经过逻辑删除）
     */
    @Delete("DELETE FROM business_type WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
