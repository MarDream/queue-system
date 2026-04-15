package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.queue.entity.Counter;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CounterMapper extends BaseMapper<Counter> {
}
