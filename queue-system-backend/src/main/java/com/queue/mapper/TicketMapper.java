package com.queue.mapper;

import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.queue.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {
    @Select("SELECT * FROM ticket WHERE status = 'waiting' AND business_type_id = #{businessTypeId} ORDER BY created_at ASC LIMIT 1")
    Ticket selectWaitingByBusinessTypeId(@Param("businessTypeId") Long businessTypeId);

    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(ticket_no, 2) AS UNSIGNED)), 0) FROM ticket WHERE business_type_id = #{businessTypeId} AND DATE(created_at) = CURDATE()")
    Long selectMaxSequenceByBusinessTypeId(@Param("businessTypeId") Long businessTypeId);
}
