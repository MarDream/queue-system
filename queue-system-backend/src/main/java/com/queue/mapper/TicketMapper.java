package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.queue.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {
    @Select("SELECT * FROM ticket WHERE status = 'waiting' AND business_type_id = #{businessTypeId} ORDER BY created_at ASC LIMIT 1")
    Ticket selectWaitingByBusinessTypeId(@Param("businessTypeId") Long businessTypeId);

    @Select("""
            SELECT COALESCE(MAX(CAST(RIGHT(ticket_no, 3) AS UNSIGNED)), 0)
            FROM ticket
            WHERE deleted = 0
              AND region_id = #{regionId}
              AND business_type_id = #{businessTypeId}
              AND created_at >= #{startOfDay}
              AND created_at < #{endOfDay}
            """)
    Long selectMaxSequenceByRegionAndBusinessTypeId(@Param("regionId") Long regionId,
                                                    @Param("businessTypeId") Long businessTypeId,
                                                    @Param("startOfDay") LocalDateTime startOfDay,
                                                    @Param("endOfDay") LocalDateTime endOfDay);

    @Select("SELECT id, region_id, business_type_id FROM ticket WHERE deleted = 0 AND status = 'waiting' AND created_at < #{cutoff}")
    List<Ticket> selectExpiredWaitingTickets(@Param("cutoff") LocalDateTime cutoff);

    @Select("SELECT id FROM ticket WHERE deleted = 0 AND counter_id IS NOT NULL AND status IN ('waiting', 'called', 'serving') AND created_at < #{cutoff}")
    List<Long> selectExpiredAssignedTicketIds(@Param("cutoff") LocalDateTime cutoff);

    @Update("UPDATE ticket " +
            "SET status = 'skipped', skip_type = 'system', updated_at = NOW() " +
            "WHERE deleted = 0 AND status IN ('waiting', 'called', 'serving') AND created_at < #{cutoff}")
    int markExpiredTicketsBefore(@Param("cutoff") LocalDateTime cutoff);
}
