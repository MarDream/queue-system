package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.queue.entity.CounterOperator;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface CounterOperatorMapper extends BaseMapper<CounterOperator> {

    @Select("SELECT user_id FROM counter_operator WHERE counter_id = #{counterId}")
    List<Long> selectUserIdsByCounterId(@Param("counterId") Long counterId);

    @Select("SELECT counter_id FROM counter_operator WHERE user_id = #{userId}")
    List<Long> selectCounterIdsByUserId(@Param("userId") Long userId);

    @Delete("DELETE FROM counter_operator WHERE counter_id = #{counterId}")
    void deleteByCounterId(@Param("counterId") Long counterId);

    @Select("SELECT u.id, u.username, u.name, u.role, u.region_id FROM sys_user u " +
            "INNER JOIN counter_operator co ON u.id = co.user_id " +
            "WHERE co.counter_id = #{counterId} AND u.deleted = 0")
    List<com.queue.entity.SysUser> selectOperatorsByCounterId(@Param("counterId") Long counterId);
}
