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

    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(ticket_no, 7) AS UNSIGNED)), 0) FROM ticket WHERE region_id = #{regionId} AND business_type_id = #{businessTypeId} AND DATE(created_at) = CURDATE()")
    Long selectMaxSequenceByRegionAndBusinessTypeId(@Param("regionId") Long regionId, @Param("businessTypeId") Long businessTypeId);
}
