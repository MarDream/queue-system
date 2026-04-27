package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.queue.entity.Counter;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CounterMapper extends BaseMapper<Counter> {

    /**
     * 物理删除（不经过逻辑删除）
     */
    @Delete("DELETE FROM counter WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
