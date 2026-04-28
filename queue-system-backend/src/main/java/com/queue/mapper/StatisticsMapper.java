package com.queue.mapper;

import com.queue.dto.StatisticsRecordVO;
import com.queue.dto.StatisticsRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StatisticsMapper {

    String SELECT_COLUMNS = """
            SELECT
              r.region_name AS regionName,
              bt.name AS businessName,
              c.name AS counterName,
              c.operator_name AS operatorName,
              t.status AS ticketStatus,
              IFNULL(t.name, '-') AS customerName,
              t.ticket_no AS ticketNo,
              t.created_at AS createdAt,
              t.called_at AS calledAt,
              t.served_at AS servedAt,
              t.completed_at AS completedAt,
              TIMESTAMPDIFF(SECOND, t.served_at, t.completed_at) AS durationSeconds,
              t.skip_type AS skipType
            """;

    String BASE_FROM = """
            FROM ticket t
            LEFT JOIN region r ON t.region_id = r.id
            LEFT JOIN business_type bt ON t.business_type_id = bt.id
            LEFT JOIN counter c ON t.counter_id = c.id
            WHERE t.deleted = 0
            <if test='request.allowedRegionIds != null and request.allowedRegionIds.size() > 0'>
              AND t.region_id IN
              <foreach collection='request.allowedRegionIds' item='rid' open='(' separator=',' close=')'>
                #{rid}
              </foreach>
            </if>
            <if test='request.regionId != null'> AND t.region_id = #{request.regionId}</if>
            <if test='request.businessTypeId != null'> AND t.business_type_id = #{request.businessTypeId}</if>
            <if test='request.createdAtStart != null'> AND t.created_at &gt;= #{request.createdAtStart}</if>
            <if test='request.createdAtEndExclusive != null'> AND t.created_at &lt; #{request.createdAtEndExclusive}</if>
            <if test='request.status != null and request.status != ""'>
              <choose>
                <when test='request.status == "skipped_system"'> AND t.status = 'skipped' AND t.skip_type = 'system'</when>
                <otherwise> AND t.status = #{request.status}</otherwise>
              </choose>
            </if>
            <if test='request.skipType != null and request.skipType != ""'> AND t.skip_type = #{request.skipType}</if>
            """;

    String ORDER_BY = """
            <if test='request.sortProp != null and request.sortProp != ""'>
              ORDER BY
              <choose>
                <when test='request.sortProp == "regionName"'>r.region_name</when>
                <when test='request.sortProp == "businessName"'>bt.name</when>
                <when test='request.sortProp == "counterName"'>c.name</when>
                <when test='request.sortProp == "operatorName"'>c.operator_name</when>
                <when test='request.sortProp == "ticketStatus"'>t.status</when>
                <when test='request.sortProp == "customerName"'>t.name</when>
                <when test='request.sortProp == "ticketNo"'>t.ticket_no</when>
                <when test='request.sortProp == "createdAt"'>t.created_at</when>
                <when test='request.sortProp == "calledAt"'>t.called_at</when>
                <when test='request.sortProp == "servedAt"'>t.served_at</when>
                <when test='request.sortProp == "completedAt"'>t.completed_at</when>
                <when test='request.sortProp == "durationSeconds"'>durationSeconds</when>
                <otherwise>t.created_at</otherwise>
              </choose>
              <choose>
                <when test='request.sortOrder == "asc"'> ASC</when>
                <otherwise> DESC</otherwise>
              </choose>
            </if>
            <if test='request.sortProp == null or request.sortProp == ""'>ORDER BY t.created_at DESC</if>
            """;

    @Select("<script>" + SELECT_COLUMNS + BASE_FROM + ORDER_BY + "</script>")
    List<StatisticsRecordVO> selectStatisticsList(@Param("request") StatisticsRequest request);

    @Select("<script>" + SELECT_COLUMNS + BASE_FROM + ORDER_BY +
            " LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<StatisticsRecordVO> selectStatisticsPage(@Param("request") StatisticsRequest request,
                                                  @Param("offset") long offset,
                                                  @Param("limit") long limit);

    @Select("<script>" +
            "SELECT COUNT(*) " +
            BASE_FROM +
            "</script>")
    Long countStatistics(@Param("request") StatisticsRequest request);
}
