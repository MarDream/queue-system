package com.queue.mapper;

import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.queue.entity.CounterBusiness;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CounterBusinessMapper extends BaseMapper<CounterBusiness> {
    @Select("SELECT business_type_id FROM counter_business WHERE counter_id = #{counterId}")
    List<Long> selectBusinessTypeIdsByCounterId(@Param("counterId") Long counterId);
}
