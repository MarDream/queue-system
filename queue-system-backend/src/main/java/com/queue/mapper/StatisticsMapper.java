package com.queue.mapper;

import com.queue.dto.StatisticsRecordVO;
import com.queue.dto.StatisticsRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StatisticsMapper {

    @Select("<script>" +
            "SELECT " +
            "  r.region_name AS regionName, " +
            "  bt.name AS businessName, " +
            "  c.name AS counterName, " +
            "  c.operator_name AS operatorName, " +
            "  t.status AS ticketStatus, " +
            "  IFNULL(t.name, '-') AS customerName, " +
            "  t.ticket_no AS ticketNo, " +
            "  t.created_at AS createdAt, " +
            "  t.called_at AS calledAt, " +
            "  t.served_at AS servedAt, " +
            "  t.completed_at AS completedAt, " +
            "  TIMESTAMPDIFF(SECOND, t.served_at, t.completed_at) AS durationSeconds " +
            "FROM ticket t " +
            "LEFT JOIN region r ON t.region_id = r.id " +
            "LEFT JOIN business_type bt ON t.business_type_id = bt.id " +
            "LEFT JOIN counter c ON t.counter_id = c.id " +
            "WHERE t.deleted = 0 " +
            "<if test='regionId != null'> AND t.region_id = #{regionId}</if>" +
            "<if test='businessTypeId != null'> AND t.business_type_id = #{businessTypeId}</if>" +
            "<if test='startDate != null'> AND DATE(t.created_at) &gt;= #{startDate}</if>" +
            "<if test='endDate != null'> AND DATE(t.created_at) &lt;= #{endDate}</if>" +
            "ORDER BY t.created_at DESC" +
            "</script>")
    List<StatisticsRecordVO> selectStatisticsList(@Param("request") StatisticsRequest request);

    @Select("<script>" +
            "SELECT COUNT(*) FROM ticket t " +
            "WHERE t.deleted = 0 " +
            "<if test='regionId != null'> AND t.region_id = #{regionId}</if>" +
            "<if name='businessTypeId != null'> AND t.business_type_id = #{businessTypeId}</if>" +
            "<if test='startDate != null'> AND DATE(t.created_at) &gt;= #{startDate}</if>" +
            "<if test='endDate != null'> AND DATE(t.created_at) &lt;= #{endDate}</if>" +
            "</script>")
    Long countStatistics(@Param("request") StatisticsRequest request);
}
