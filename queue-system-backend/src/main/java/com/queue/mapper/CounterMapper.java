package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.queue.entity.Counter;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CounterMapper extends BaseMapper<Counter> {

    /**
     * 物理删除（不经过逻辑删除）
     */
    @Delete("DELETE FROM counter WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    @Update("<script>" +
            "UPDATE counter " +
            "SET status = 'idle', current_ticket_id = NULL " +
            "WHERE current_ticket_id IN " +
            "<foreach collection='ticketIds' item='ticketId' open='(' separator=',' close=')'>#{ticketId}</foreach>" +
            "</script>")
    int resetCurrentAssignments(@Param("ticketIds") List<Long> ticketIds);

    @Update("UPDATE counter " +
            "SET status = #{status}, current_ticket_id = NULL, updated_at = NOW(), version = version + 1 " +
            "WHERE id = #{counterId}")
    int clearCurrentTicket(@Param("counterId") Long counterId, @Param("status") String status);

    /**
     * 物理删除指定区域的所有窗口
     */
    @Delete("DELETE FROM counter WHERE region_id = #{regionId}")
    int physicalDeleteByRegionId(@Param("regionId") Long regionId);
}
