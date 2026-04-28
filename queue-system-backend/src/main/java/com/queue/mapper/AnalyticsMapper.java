package com.queue.mapper;

import com.queue.dto.CounterRecentServiceRow;
import com.queue.dto.CounterStatsSummaryRow;
import com.queue.dto.DashboardBusinessTypeStatRow;
import com.queue.dto.DashboardCounterStatRow;
import com.queue.dto.DashboardOverviewRow;
import com.queue.dto.ScreenCounterStatusRow;
import com.queue.dto.ScreenCurrentCallRow;
import com.queue.dto.ScreenRecentCallRow;
import com.queue.dto.ScreenStatsRow;
import com.queue.dto.ScreenWaitingTicketRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface AnalyticsMapper {

    String REGION_FILTER_TICKET = """
            <if test='regionIds != null'>
              AND t.region_id IN
              <foreach collection='regionIds' item='regionId' open='(' separator=',' close=')'>
                #{regionId}
              </foreach>
            </if>
            """;

    String REGION_FILTER_COUNTER = """
            <if test='regionIds != null'>
              AND c.region_id IN
              <foreach collection='regionIds' item='regionId' open='(' separator=',' close=')'>
                #{regionId}
              </foreach>
            </if>
            """;

    @Select("<script>" +
            "SELECT " +
            "  COALESCE(SUM(CASE WHEN t.status = 'completed' THEN 1 ELSE 0 END), 0) AS todayServedCount, " +
            "  COALESCE(SUM(CASE WHEN t.status IN ('called', 'serving', 'completed') THEN 1 ELSE 0 END), 0) AS todayCalledCount, " +
            "  COALESCE(SUM(CASE WHEN t.status = 'skipped' THEN 1 ELSE 0 END), 0) AS todaySkippedCount, " +
            "  ROUND(AVG(CASE WHEN t.status = 'completed' AND t.served_at IS NOT NULL AND t.completed_at IS NOT NULL " +
            "    THEN TIMESTAMPDIFF(SECOND, t.served_at, t.completed_at) / 60.0 END), 1) AS avgServiceMinutes, " +
            "  ROUND(AVG(CASE WHEN t.status IN ('completed', 'skipped') AND t.called_at IS NOT NULL " +
            "    THEN TIMESTAMPDIFF(SECOND, t.created_at, t.called_at) / 60.0 END), 1) AS avgWaitMinutes " +
            "FROM ticket t " +
            "WHERE t.deleted = 0 " +
            "  AND t.counter_id = #{counterId} " +
            "  AND t.created_at &gt;= #{startOfDay} " +
            "  AND t.created_at &lt; #{endOfDay}" +
            "</script>")
    CounterStatsSummaryRow selectCounterStatsSummary(@Param("counterId") Long counterId,
                                                     @Param("startOfDay") LocalDateTime startOfDay,
                                                     @Param("endOfDay") LocalDateTime endOfDay);

    @Select("<script>" +
            "SELECT " +
            "  t.ticket_no AS ticketNo, " +
            "  bt.name AS businessTypeName, " +
            "  IFNULL(t.name, '') AS customerName, " +
            "  t.status AS status, " +
            "  t.called_at AS calledAt, " +
            "  t.completed_at AS completedAt, " +
            "  ROUND(CASE WHEN t.served_at IS NOT NULL AND t.completed_at IS NOT NULL " +
            "    THEN TIMESTAMPDIFF(SECOND, t.served_at, t.completed_at) / 60.0 END, 1) AS serviceMinutes " +
            "FROM ticket t " +
            "LEFT JOIN business_type bt ON t.business_type_id = bt.id " +
            "WHERE t.deleted = 0 " +
            "  AND t.counter_id = #{counterId} " +
            "  AND t.created_at &gt;= #{startOfDay} " +
            "  AND t.created_at &lt; #{endOfDay} " +
            "  AND (t.called_at IS NOT NULL OR t.completed_at IS NOT NULL) " +
            "ORDER BY COALESCE(t.completed_at, t.called_at) DESC " +
            "LIMIT #{limit}" +
            "</script>")
    List<CounterRecentServiceRow> selectCounterRecentServices(@Param("counterId") Long counterId,
                                                              @Param("startOfDay") LocalDateTime startOfDay,
                                                              @Param("endOfDay") LocalDateTime endOfDay,
                                                              @Param("limit") int limit);

    @Select("<script>" +
            "SELECT " +
            "  COUNT(*) AS totalToday, " +
            "  COALESCE(SUM(CASE WHEN t.status = 'waiting' THEN 1 ELSE 0 END), 0) AS waitingCount, " +
            "  COALESCE(SUM(CASE WHEN t.status = 'completed' THEN 1 ELSE 0 END), 0) AS completedCount, " +
            "  COALESCE(SUM(CASE WHEN t.status = 'skipped' THEN 1 ELSE 0 END), 0) AS skippedCount " +
            "FROM ticket t " +
            "WHERE t.deleted = 0 " +
            "  AND t.created_at &gt;= #{startOfDay} " +
            "  AND t.created_at &lt; #{endOfDay} " +
            REGION_FILTER_TICKET +
            "</script>")
    ScreenStatsRow selectScreenStats(@Param("regionIds") Collection<Long> regionIds,
                                     @Param("startOfDay") LocalDateTime startOfDay,
                                     @Param("endOfDay") LocalDateTime endOfDay);

    @Select("<script>" +
            "SELECT " +
            "  t.ticket_no AS ticketNo, " +
            "  c.id AS counterId, " +
            "  c.name AS counterName, " +
            "  t.called_at AS calledAt, " +
            "  bt.name AS businessTypeName " +
            "FROM ticket t " +
            "LEFT JOIN counter c ON t.counter_id = c.id " +
            "LEFT JOIN business_type bt ON t.business_type_id = bt.id " +
            "WHERE t.deleted = 0 " +
            "  AND t.created_at &gt;= #{startOfDay} " +
            "  AND t.created_at &lt; #{endOfDay} " +
            "  AND t.status IN ('called', 'serving') " +
            REGION_FILTER_TICKET +
            "ORDER BY t.called_at DESC" +
            "</script>")
    List<ScreenCurrentCallRow> selectScreenCurrentCalls(@Param("regionIds") Collection<Long> regionIds,
                                                        @Param("startOfDay") LocalDateTime startOfDay,
                                                        @Param("endOfDay") LocalDateTime endOfDay);

    @Select("<script>" +
            "SELECT " +
            "  t.id AS id, " +
            "  t.ticket_no AS ticketNo, " +
            "  bt.name AS businessTypeName, " +
            "  CASE WHEN t.reactivated_at IS NULL THEN FALSE ELSE TRUE END AS reactivated, " +
            "  t.created_at AS createdAt " +
            "FROM ticket t " +
            "LEFT JOIN business_type bt ON t.business_type_id = bt.id " +
            "WHERE t.deleted = 0 " +
            "  AND t.created_at &gt;= #{startOfDay} " +
            "  AND t.created_at &lt; #{endOfDay} " +
            "  AND t.status = 'waiting' " +
            REGION_FILTER_TICKET +
            "ORDER BY IF(t.reactivated_at IS NULL, 1, 0) ASC, t.reactivated_at ASC, t.created_at ASC " +
            "LIMIT #{limit}" +
            "</script>")
    List<ScreenWaitingTicketRow> selectScreenWaitingQueue(@Param("regionIds") Collection<Long> regionIds,
                                                          @Param("startOfDay") LocalDateTime startOfDay,
                                                          @Param("endOfDay") LocalDateTime endOfDay,
                                                          @Param("limit") int limit);

    @Select("<script>" +
            "SELECT " +
            "  t.ticket_no AS ticketNo, " +
            "  c.name AS counterName, " +
            "  t.called_at AS calledAt, " +
            "  bt.name AS businessTypeName " +
            "FROM ticket t " +
            "LEFT JOIN counter c ON t.counter_id = c.id " +
            "LEFT JOIN business_type bt ON t.business_type_id = bt.id " +
            "WHERE t.deleted = 0 " +
            "  AND t.created_at &gt;= #{startOfDay} " +
            "  AND t.created_at &lt; #{endOfDay} " +
            "  AND t.status = 'skipped' " +
            "  AND t.called_at IS NOT NULL " +
            "  AND t.counter_id IS NOT NULL " +
            REGION_FILTER_TICKET +
            "ORDER BY t.called_at DESC " +
            "LIMIT #{limit}" +
            "</script>")
    List<ScreenRecentCallRow> selectScreenRecentCalls(@Param("regionIds") Collection<Long> regionIds,
                                                      @Param("startOfDay") LocalDateTime startOfDay,
                                                      @Param("endOfDay") LocalDateTime endOfDay,
                                                      @Param("limit") int limit);

    @Select("<script>" +
            "SELECT " +
            "  c.id AS counterId, " +
            "  c.name AS name, " +
            "  c.status AS status, " +
            "  CASE WHEN t.status IN ('called', 'serving') THEN t.ticket_no ELSE '' END AS currentTicketNo " +
            "FROM counter c " +
            "LEFT JOIN ticket t ON c.current_ticket_id = t.id AND t.deleted = 0 " +
            "WHERE 1 = 1 " +
            REGION_FILTER_COUNTER +
            "ORDER BY c.id ASC" +
            "</script>")
    List<ScreenCounterStatusRow> selectScreenCounterStatuses(@Param("regionIds") Collection<Long> regionIds);

    @Select("<script>" +
            "SELECT " +
            "  COUNT(*) AS totalTickets, " +
            "  COALESCE(SUM(CASE WHEN t.status = 'completed' THEN 1 ELSE 0 END), 0) AS completedCount, " +
            "  COALESCE(SUM(CASE WHEN t.status = 'waiting' THEN 1 ELSE 0 END), 0) AS waitingCount, " +
            "  ROUND(AVG(CASE WHEN t.status IN ('completed', 'skipped') AND t.called_at IS NOT NULL " +
            "    THEN TIMESTAMPDIFF(SECOND, t.created_at, t.called_at) / 60.0 END), 1) AS avgWaitMinutes, " +
            "  ROUND(AVG(CASE WHEN t.status = 'completed' AND t.served_at IS NOT NULL AND t.completed_at IS NOT NULL " +
            "    THEN TIMESTAMPDIFF(SECOND, t.served_at, t.completed_at) / 60.0 END), 1) AS avgServeMinutes " +
            "FROM ticket t " +
            "WHERE t.deleted = 0 " +
            "  AND t.created_at &gt;= #{startOfDay} " +
            "  AND t.created_at &lt; #{endOfDay} " +
            REGION_FILTER_TICKET +
            "</script>")
    DashboardOverviewRow selectDashboardOverview(@Param("regionIds") Collection<Long> regionIds,
                                                 @Param("startOfDay") LocalDateTime startOfDay,
                                                 @Param("endOfDay") LocalDateTime endOfDay);

    @Select("<script>" +
            "SELECT " +
            "  c.name AS counterName, " +
            "  COALESCE(x.completedCount, 0) AS completedCount " +
            "FROM counter c " +
            "LEFT JOIN (" +
            "  SELECT t.counter_id AS counterId, COUNT(*) AS completedCount " +
            "  FROM ticket t " +
            "  WHERE t.deleted = 0 " +
            "    AND t.status = 'completed' " +
            "    AND t.created_at &gt;= #{startOfDay} " +
            "    AND t.created_at &lt; #{endOfDay} " +
            REGION_FILTER_TICKET +
            "  GROUP BY t.counter_id" +
            ") x ON x.counterId = c.id " +
            "WHERE 1 = 1 " +
            REGION_FILTER_COUNTER +
            "ORDER BY c.id ASC" +
            "</script>")
    List<DashboardCounterStatRow> selectDashboardCounterStats(@Param("regionIds") Collection<Long> regionIds,
                                                              @Param("startOfDay") LocalDateTime startOfDay,
                                                              @Param("endOfDay") LocalDateTime endOfDay);

    @Select("<script>" +
            "SELECT " +
            "  bt.id AS businessTypeId, " +
            "  bt.name AS businessType, " +
            "  COALESCE(x.ticketCount, 0) AS count " +
            "FROM business_type bt " +
            "LEFT JOIN (" +
            "  SELECT t.business_type_id AS businessTypeId, COUNT(*) AS ticketCount " +
            "  FROM ticket t " +
            "  WHERE t.deleted = 0 " +
            "    AND t.created_at &gt;= #{startOfDay} " +
            "    AND t.created_at &lt; #{endOfDay} " +
            REGION_FILTER_TICKET +
            "  GROUP BY t.business_type_id" +
            ") x ON x.businessTypeId = bt.id " +
            "ORDER BY bt.id ASC" +
            "</script>")
    List<DashboardBusinessTypeStatRow> selectDashboardBusinessTypeStats(@Param("regionIds") Collection<Long> regionIds,
                                                                        @Param("startOfDay") LocalDateTime startOfDay,
                                                                        @Param("endOfDay") LocalDateTime endOfDay);

    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM ticket t " +
            "WHERE t.deleted = 0 " +
            "  AND t.status = 'waiting' " +
            "  AND t.created_at &gt;= #{startOfDay} " +
            "  AND t.created_at &lt; #{endOfDay} " +
            "  AND t.region_id IN " +
            "  <foreach collection='regionIds' item='regionId' open='(' separator=',' close=')'>#{regionId}</foreach> " +
            "  AND t.business_type_id IN " +
            "  <foreach collection='businessTypeIds' item='businessTypeId' open='(' separator=',' close=')'>#{businessTypeId}</foreach>" +
            "</script>")
    Long countCounterWaitingTickets(@Param("regionIds") Collection<Long> regionIds,
                                    @Param("businessTypeIds") Collection<Long> businessTypeIds,
                                    @Param("startOfDay") LocalDateTime startOfDay,
                                    @Param("endOfDay") LocalDateTime endOfDay);

    @Select("<script>" +
            "SELECT " +
            "  t.id AS id, " +
            "  t.ticket_no AS ticketNo, " +
            "  bt.name AS businessTypeName, " +
            "  CASE WHEN t.reactivated_at IS NULL THEN FALSE ELSE TRUE END AS reactivated, " +
            "  t.created_at AS createdAt " +
            "FROM ticket t " +
            "LEFT JOIN business_type bt ON t.business_type_id = bt.id " +
            "WHERE t.deleted = 0 " +
            "  AND t.status = 'waiting' " +
            "  AND t.created_at &gt;= #{startOfDay} " +
            "  AND t.created_at &lt; #{endOfDay} " +
            "  AND t.region_id IN " +
            "  <foreach collection='regionIds' item='regionId' open='(' separator=',' close=')'>#{regionId}</foreach> " +
            "  AND t.business_type_id IN " +
            "  <foreach collection='businessTypeIds' item='businessTypeId' open='(' separator=',' close=')'>#{businessTypeId}</foreach> " +
            "ORDER BY IF(t.reactivated_at IS NULL, 1, 0) ASC, t.reactivated_at ASC, t.created_at ASC " +
            "LIMIT #{limit}" +
            "</script>")
    List<ScreenWaitingTicketRow> selectCounterWaitingQueue(@Param("regionIds") Collection<Long> regionIds,
                                                           @Param("businessTypeIds") Collection<Long> businessTypeIds,
                                                           @Param("startOfDay") LocalDateTime startOfDay,
                                                           @Param("endOfDay") LocalDateTime endOfDay,
                                                           @Param("limit") int limit);
}
