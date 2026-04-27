package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.queue.common.BusinessException;
import com.queue.common.ResultCode;
import com.queue.dto.AiAskRequest;
import com.queue.dto.AiAskResponse;
import com.queue.dto.AiMessageDTO;
import com.queue.dto.AiSessionDTO;
import com.queue.entity.BusinessType;
import com.queue.entity.Counter;
import com.queue.entity.SysUser;
import com.queue.entity.Ticket;
import com.queue.enums.TicketStatus;
import com.queue.mapper.BusinessTypeMapper;
import com.queue.mapper.CounterBusinessMapper;
import com.queue.mapper.CounterMapper;
import com.queue.mapper.CounterOperatorMapper;
import com.queue.mapper.RegionMapper;
import com.queue.mapper.SysConfigMapper;
import com.queue.mapper.SysUserMapper;
import com.queue.mapper.TicketMapper;
import com.queue.service.AiQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiQueryServiceImpl implements AiQueryService {

    private final SysUserMapper sysUserMapper;
    private final TicketMapper ticketMapper;
    private final CounterMapper counterMapper;
    private final CounterOperatorMapper counterOperatorMapper;
    private final CounterBusinessMapper counterBusinessMapper;
    private final BusinessTypeMapper businessTypeMapper;
    private final RegionMapper regionMapper;
    private final SysConfigMapper sysConfigMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public AiAskResponse ask(Long userId, AiAskRequest request) {
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "未登录");
        }
        AiAskRequest req = request == null ? new AiAskRequest() : request;
        String workspace = normalizeWorkspace(req.getWorkspace());
        validateWorkspacePermission(user, workspace);

        String q = req.getQuestion();
        q = q == null ? "" : q.trim();
        if (q.isEmpty()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "请输入问题");
        }

        String sessionId = req.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
        }

        QueryScope scope = resolveScope(user, workspace, req);
        TimeRange range = resolveTimeRange(q);
        QueryIntent intent = resolveIntent(q);
        QueryType queryType = resolveQueryType(q);

        AiAskResponse resp = new AiAskResponse();
        resp.setSessionId(sessionId);
        resp.setQueryType(queryType.name().toLowerCase());
        resp.setIntent(intent.name().toLowerCase());
        List<AiAskResponse.AiCard> cards = new ArrayList<>();
        resp.setCapabilities(List.of("cards", "table", "export", "sessions"));

        QueryResult result = executeQuery(queryType, intent, q, range, scope, req);
        resp.setAnswer(result.answer);
        if (result.cards != null) {
            cards.addAll(result.cards);
        }
        resp.setCards(cards);
        resp.setTableColumns(result.tableColumns);
        resp.setTableRows(result.tableRows);

        persistConversation(user.getId(), workspace, sessionId, q, resp);
        return resp;
    }

    @Override
    public List<AiSessionDTO> listSessions(Long userId, String workspace) {
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "未登录");
        }
        String ws = normalizeWorkspace(workspace);
        validateWorkspacePermission(user, ws);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(sessionIndexKey(user.getId(), ws));
        return entries.values().stream()
            .filter(v -> v instanceof AiSessionDTO)
            .map(v -> (AiSessionDTO) v)
            .sorted(Comparator.comparing(AiSessionDTO::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());
    }

    @Override
    public List<AiMessageDTO> getMessages(Long userId, String workspace, String sessionId) {
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "未登录");
        }
        String ws = normalizeWorkspace(workspace);
        validateWorkspacePermission(user, ws);
        if (sessionId == null || sessionId.isBlank()) {
            return new ArrayList<>();
        }
        List<Object> raw = redisTemplate.opsForList().range(messagesKey(user.getId(), ws, sessionId), 0, -1);
        if (raw == null) {
            return new ArrayList<>();
        }
        List<AiMessageDTO> msgs = new ArrayList<>();
        for (Object o : raw) {
            if (o instanceof AiMessageDTO) {
                msgs.add((AiMessageDTO) o);
            }
        }
        return msgs;
    }

    @Override
    public void deleteSession(Long userId, String workspace, String sessionId) {
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "未登录");
        }
        String ws = normalizeWorkspace(workspace);
        validateWorkspacePermission(user, ws);
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        redisTemplate.delete(messagesKey(user.getId(), ws, sessionId));
        redisTemplate.delete(lastKey(user.getId(), ws, sessionId));
        redisTemplate.opsForHash().delete(sessionIndexKey(user.getId(), ws), sessionId);
    }

    @Override
    public Map<String, Object> getLastTable(Long userId, String workspace, String sessionId) {
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "未登录");
        }
        String ws = normalizeWorkspace(workspace);
        validateWorkspacePermission(user, ws);
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        Object v = redisTemplate.opsForValue().get(lastKey(user.getId(), ws, sessionId));
        if (v instanceof Map) {
            return (Map<String, Object>) v;
        }
        return null;
    }

    private AiAskResponse.AiCard card(String title, String value, String unit) {
        AiAskResponse.AiCard c = new AiAskResponse.AiCard();
        c.setTitle(title);
        c.setValue(value);
        c.setUnit(unit);
        return c;
    }

    private AiAskResponse.AiTableColumn column(String key, String label) {
        AiAskResponse.AiTableColumn c = new AiAskResponse.AiTableColumn();
        c.setKey(key);
        c.setLabel(label);
        return c;
    }

    private enum QueryType {
        KPI,
        RANK,
        TREND,
        DETAIL
    }

    private enum QueryIntent {
        WAITING_TOTAL,
        TAKE_COUNT,
        COMPLETED_COUNT,
        SKIPPED_COUNT,
        UNKNOWN
    }

    private QueryIntent resolveIntent(String q) {
        if (containsAny(q, "等待", "排队")) {
            return QueryIntent.WAITING_TOTAL;
        }
        if (containsAny(q, "办结", "完成")) {
            return QueryIntent.COMPLETED_COUNT;
        }
        if (containsAny(q, "过号", "跳过")) {
            return QueryIntent.SKIPPED_COUNT;
        }
        if (containsAny(q, "取号", "领号")) {
            return QueryIntent.TAKE_COUNT;
        }
        return QueryIntent.UNKNOWN;
    }

    private QueryType resolveQueryType(String q) {
        if (containsAny(q, "明细", "列表", "记录", "清单")) {
            return QueryType.DETAIL;
        }
        if (containsAny(q, "排名", "排行", "top", "TOP")) {
            return QueryType.RANK;
        }
        if (containsAny(q, "趋势", "曲线", "按天", "每天")) {
            return QueryType.TREND;
        }
        return QueryType.KPI;
    }

    private static class TimeRange {
        private final String label;
        private final LocalDateTime start;
        private final LocalDateTime end;

        private TimeRange(String label, LocalDateTime start, LocalDateTime end) {
            this.label = label;
            this.start = start;
            this.end = end;
        }
    }

    private TimeRange resolveTimeRange(String q) {
        LocalDate today = LocalDate.now();
        if (containsAny(q, "昨天", "昨日")) {
            LocalDate d = today.minusDays(1);
            return new TimeRange("昨日", d.atStartOfDay(), d.plusDays(1).atStartOfDay());
        }
        if (containsAny(q, "近7天", "最近7天")) {
            LocalDate start = today.minusDays(6);
            return new TimeRange("近7天", start.atStartOfDay(), today.plusDays(1).atStartOfDay());
        }
        if (containsAny(q, "今天", "今日")) {
            return new TimeRange("今日", today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        }
        return new TimeRange("今日", today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    private boolean containsAny(String s, String... tokens) {
        for (String t : tokens) {
            if (s.contains(t)) return true;
        }
        return false;
    }

    private static class QueryScope {
        private final String workspace;
        private final Long regionId;
        private final Long counterId;
        private final List<Long> businessTypeIds;
        private final boolean counterBound;

        private QueryScope(String workspace, Long regionId, Long counterId, List<Long> businessTypeIds, boolean counterBound) {
            this.workspace = workspace;
            this.regionId = regionId;
            this.counterId = counterId;
            this.businessTypeIds = businessTypeIds;
            this.counterBound = counterBound;
        }
    }

    private static class QueryResult {
        private final String answer;
        private final List<AiAskResponse.AiCard> cards;
        private final List<AiAskResponse.AiTableColumn> tableColumns;
        private final List<Map<String, Object>> tableRows;

        private QueryResult(String answer,
                            List<AiAskResponse.AiCard> cards,
                            List<AiAskResponse.AiTableColumn> tableColumns,
                            List<Map<String, Object>> tableRows) {
            this.answer = answer;
            this.cards = cards;
            this.tableColumns = tableColumns;
            this.tableRows = tableRows;
        }
    }

    private QueryResult executeQuery(QueryType queryType,
                                    QueryIntent intent,
                                    String q,
                                    TimeRange range,
                                    QueryScope scope,
                                    AiAskRequest req) {
        if (intent == QueryIntent.UNKNOWN) {
            return new QueryResult("已支持：等待/取号/办结/过号 的 KPI、趋势、排名、明细（可导出）。", new ArrayList<>(), null, null);
        }

        if (queryType == QueryType.KPI) {
            return executeKpi(intent, range, scope);
        }
        if (queryType == QueryType.RANK) {
            return executeRank(intent, range, scope);
        }
        if (queryType == QueryType.TREND) {
            return executeTrend(intent, scope);
        }
        return executeDetail(intent, range, scope, req);
    }

    private QueryResult executeKpi(QueryIntent intent, TimeRange range, QueryScope scope) {
        List<AiAskResponse.AiCard> cards = new ArrayList<>();

        if (intent == QueryIntent.WAITING_TOTAL) {
            long count = ticketMapper.selectCount(baseWrapper(scope)
                .eq(Ticket::getStatus, TicketStatus.WAITING.getValue()));
            String label = scope.counterBound ? "当前等待(本窗口业务)" : "当前等待";
            cards.add(card(label, String.valueOf(count), "人"));
            return new QueryResult(label + "：" + count, cards, null, null);
        }
        if (intent == QueryIntent.TAKE_COUNT) {
            long count = ticketMapper.selectCount(baseWrapper(scope)
                .ge(Ticket::getCreatedAt, range.start)
                .lt(Ticket::getCreatedAt, range.end));
            String label = range.label + (scope.counterBound ? "取号(本窗口业务)" : "取号");
            cards.add(card(label, String.valueOf(count), "张"));
            return new QueryResult(label + "：" + count, cards, null, null);
        }
        if (intent == QueryIntent.COMPLETED_COUNT) {
            LambdaQueryWrapper<Ticket> wrapper = baseWrapper(scope)
                .eq(Ticket::getStatus, TicketStatus.COMPLETED.getValue())
                .isNotNull(Ticket::getCompletedAt)
                .ge(Ticket::getCompletedAt, range.start)
                .lt(Ticket::getCompletedAt, range.end);
            if (scope.counterId != null && scope.counterBound) {
                wrapper.eq(Ticket::getCounterId, scope.counterId);
            }
            long count = ticketMapper.selectCount(wrapper);
            String label = range.label + (scope.counterBound ? "办结(本窗口)" : "办结");
            cards.add(card(label, String.valueOf(count), "张"));
            return new QueryResult(label + "：" + count, cards, null, null);
        }
        if (intent == QueryIntent.SKIPPED_COUNT) {
            LambdaQueryWrapper<Ticket> wrapper = baseWrapper(scope)
                .eq(Ticket::getStatus, TicketStatus.SKIPPED.getValue())
                .isNotNull(Ticket::getUpdatedAt)
                .ge(Ticket::getUpdatedAt, range.start)
                .lt(Ticket::getUpdatedAt, range.end);
            if (scope.counterId != null && scope.counterBound) {
                wrapper.eq(Ticket::getCounterId, scope.counterId);
            }
            long count = ticketMapper.selectCount(wrapper);
            String label = range.label + (scope.counterBound ? "过号(本窗口)" : "过号");
            cards.add(card(label, String.valueOf(count), "张"));
            return new QueryResult(label + "：" + count, cards, null, null);
        }
        return new QueryResult("暂不支持该 KPI。", cards, null, null);
    }

    private QueryResult executeRank(QueryIntent intent, TimeRange range, QueryScope scope) {
        List<AiAskResponse.AiTableColumn> cols = List.of(
            column("name", "业务"),
            column("count", "数量")
        );

        QueryWrapper<Ticket> wrapper = new QueryWrapper<Ticket>()
            .eq("deleted", 0);
        if (scope.regionId != null) {
            wrapper.eq("region_id", scope.regionId);
        }
        if (scope.businessTypeIds != null && !scope.businessTypeIds.isEmpty()) {
            wrapper.in("business_type_id", scope.businessTypeIds);
        }

        if (intent == QueryIntent.TAKE_COUNT) {
            wrapper.ge("created_at", range.start).lt("created_at", range.end);
        } else if (intent == QueryIntent.COMPLETED_COUNT) {
            wrapper.eq("status", TicketStatus.COMPLETED.getValue())
                .isNotNull("completed_at")
                .ge("completed_at", range.start)
                .lt("completed_at", range.end);
            if (scope.counterId != null && scope.counterBound) {
                wrapper.eq("counter_id", scope.counterId);
            }
        } else if (intent == QueryIntent.SKIPPED_COUNT) {
            wrapper.eq("status", TicketStatus.SKIPPED.getValue())
                .isNotNull("updated_at")
                .ge("updated_at", range.start)
                .lt("updated_at", range.end);
            if (scope.counterId != null && scope.counterBound) {
                wrapper.eq("counter_id", scope.counterId);
            }
        } else {
            wrapper.eq("status", TicketStatus.WAITING.getValue());
        }

        wrapper.select("business_type_id", "COUNT(*) AS cnt")
            .groupBy("business_type_id")
            .orderByDesc("cnt")
            .last("LIMIT 10");

        List<Map<String, Object>> raw = ticketMapper.selectMaps(wrapper);

        Map<Long, String> btNames = loadBusinessTypeNames(raw.stream()
            .map(m -> asLong(m.get("business_type_id")))
            .filter(v -> v != null)
            .collect(Collectors.toList()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> m : raw) {
            Long btId = asLong(m.get("business_type_id"));
            Long cnt = asLong(m.get("cnt"));
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", btNames.getOrDefault(btId, btId == null ? "-" : String.valueOf(btId)));
            r.put("count", cnt == null ? 0 : cnt);
            rows.add(r);
        }

        String metric = intent == QueryIntent.TAKE_COUNT ? "取号" :
            intent == QueryIntent.COMPLETED_COUNT ? "办结" :
                intent == QueryIntent.SKIPPED_COUNT ? "过号" : "等待";
        String answer = range.label + metric + " TOP10（按业务）";
        return new QueryResult(answer, new ArrayList<>(), cols, rows);
    }

    private QueryResult executeTrend(QueryIntent intent, QueryScope scope) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);

        List<AiAskResponse.AiTableColumn> cols = List.of(
            column("date", "日期"),
            column("count", "数量")
        );

        List<Map<String, Object>> rows = new ArrayList<>();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            LocalDateTime s = d.atStartOfDay();
            LocalDateTime e = d.plusDays(1).atStartOfDay();
            long cnt;
            if (intent == QueryIntent.TAKE_COUNT) {
                cnt = ticketMapper.selectCount(baseWrapper(scope).ge(Ticket::getCreatedAt, s).lt(Ticket::getCreatedAt, e));
            } else if (intent == QueryIntent.COMPLETED_COUNT) {
                LambdaQueryWrapper<Ticket> w = baseWrapper(scope)
                    .eq(Ticket::getStatus, TicketStatus.COMPLETED.getValue())
                    .isNotNull(Ticket::getCompletedAt)
                    .ge(Ticket::getCompletedAt, s)
                    .lt(Ticket::getCompletedAt, e);
                if (scope.counterId != null && scope.counterBound) {
                    w.eq(Ticket::getCounterId, scope.counterId);
                }
                cnt = ticketMapper.selectCount(w);
            } else if (intent == QueryIntent.SKIPPED_COUNT) {
                LambdaQueryWrapper<Ticket> w = baseWrapper(scope)
                    .eq(Ticket::getStatus, TicketStatus.SKIPPED.getValue())
                    .isNotNull(Ticket::getUpdatedAt)
                    .ge(Ticket::getUpdatedAt, s)
                    .lt(Ticket::getUpdatedAt, e);
                if (scope.counterId != null && scope.counterBound) {
                    w.eq(Ticket::getCounterId, scope.counterId);
                }
                cnt = ticketMapper.selectCount(w);
            } else {
                cnt = ticketMapper.selectCount(baseWrapper(scope).eq(Ticket::getStatus, TicketStatus.WAITING.getValue()));
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("date", d.format(df));
            r.put("count", cnt);
            rows.add(r);
        }

        String metric = intent == QueryIntent.TAKE_COUNT ? "取号" :
            intent == QueryIntent.COMPLETED_COUNT ? "办结" :
                intent == QueryIntent.SKIPPED_COUNT ? "过号" : "等待";
        String answer = "近7天" + metric + "趋势";
        return new QueryResult(answer, new ArrayList<>(), cols, rows);
    }

    private QueryResult executeDetail(QueryIntent intent, TimeRange range, QueryScope scope, AiAskRequest req) {
        int limit = req.getLimit() != null && req.getLimit() > 0 ? req.getLimit() : readIntConfig("ai.query.limit", 200);
        limit = Math.min(limit, 2000);

        LambdaQueryWrapper<Ticket> wrapper = baseWrapper(scope).orderByDesc(Ticket::getCreatedAt).last("LIMIT " + limit);
        if (intent == QueryIntent.TAKE_COUNT) {
            wrapper.ge(Ticket::getCreatedAt, range.start).lt(Ticket::getCreatedAt, range.end);
        } else if (intent == QueryIntent.COMPLETED_COUNT) {
            wrapper.eq(Ticket::getStatus, TicketStatus.COMPLETED.getValue())
                .isNotNull(Ticket::getCompletedAt)
                .ge(Ticket::getCompletedAt, range.start)
                .lt(Ticket::getCompletedAt, range.end);
            if (scope.counterId != null && scope.counterBound) {
                wrapper.eq(Ticket::getCounterId, scope.counterId);
            }
        } else if (intent == QueryIntent.SKIPPED_COUNT) {
            wrapper.eq(Ticket::getStatus, TicketStatus.SKIPPED.getValue())
                .isNotNull(Ticket::getUpdatedAt)
                .ge(Ticket::getUpdatedAt, range.start)
                .lt(Ticket::getUpdatedAt, range.end);
            if (scope.counterId != null && scope.counterBound) {
                wrapper.eq(Ticket::getCounterId, scope.counterId);
            }
        } else if (intent == QueryIntent.WAITING_TOTAL) {
            wrapper.eq(Ticket::getStatus, TicketStatus.WAITING.getValue());
        }

        List<Ticket> tickets = ticketMapper.selectList(wrapper);
        List<Long> btIds = tickets.stream().map(Ticket::getBusinessTypeId).distinct().collect(Collectors.toList());
        Map<Long, String> btNames = loadBusinessTypeNames(btIds);
        Map<Long, String> regionNames = loadRegionNames(tickets.stream().map(Ticket::getRegionId).distinct().collect(Collectors.toList()));

        List<AiAskResponse.AiTableColumn> cols = List.of(
            column("region", "区域"),
            column("biz", "业务"),
            column("ticketNo", "票号"),
            column("status", "状态"),
            column("createdAt", "取号时间"),
            column("calledAt", "叫号时间"),
            column("servedAt", "开始服务"),
            column("completedAt", "完成时间"),
            column("skipType", "过号来源")
        );

        DateTimeFormatter dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Ticket t : tickets) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("region", regionNames.getOrDefault(t.getRegionId(), t.getRegionId() == null ? "-" : String.valueOf(t.getRegionId())));
            r.put("biz", btNames.getOrDefault(t.getBusinessTypeId(), t.getBusinessTypeId() == null ? "-" : String.valueOf(t.getBusinessTypeId())));
            r.put("ticketNo", t.getTicketNo());
            r.put("status", t.getStatus());
            r.put("createdAt", t.getCreatedAt() == null ? "" : t.getCreatedAt().format(dt));
            r.put("calledAt", t.getCalledAt() == null ? "" : t.getCalledAt().format(dt));
            r.put("servedAt", t.getServedAt() == null ? "" : t.getServedAt().format(dt));
            r.put("completedAt", t.getCompletedAt() == null ? "" : t.getCompletedAt().format(dt));
            r.put("skipType", t.getSkipType() == null ? "" : t.getSkipType());
            rows.add(r);
        }

        String metric = intent == QueryIntent.TAKE_COUNT ? "取号" :
            intent == QueryIntent.COMPLETED_COUNT ? "办结" :
                intent == QueryIntent.SKIPPED_COUNT ? "过号" : "等待";
        String answer = range.label + metric + "明细（最多 " + limit + " 条）";
        return new QueryResult(answer, new ArrayList<>(), cols, rows);
    }

    private LambdaQueryWrapper<Ticket> baseWrapper(QueryScope scope) {
        LambdaQueryWrapper<Ticket> w = new LambdaQueryWrapper<Ticket>().eq(Ticket::getDeleted, 0);
        if (scope.regionId != null) {
            w.eq(Ticket::getRegionId, scope.regionId);
        }
        if (scope.businessTypeIds != null && !scope.businessTypeIds.isEmpty()) {
            w.in(Ticket::getBusinessTypeId, scope.businessTypeIds);
        }
        return w;
    }

    private String normalizeWorkspace(String workspace) {
        String ws = workspace == null ? "" : workspace.trim().toLowerCase();
        if (ws.isEmpty()) {
            return "admin";
        }
        if (!ws.equals("admin") && !ws.equals("counter")) {
            return "admin";
        }
        return ws;
    }

    private void validateWorkspacePermission(SysUser user, String workspace) {
        if ("admin".equals(workspace)) {
            if (!"SUPER_ADMIN".equals(user.getRole()) && !"REGION_ADMIN".equals(user.getRole())) {
                throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "无权使用智能问数");
            }
            return;
        }
        if ("counter".equals(workspace)) {
            if (!"SUPER_ADMIN".equals(user.getRole()) && !"WINDOW_OPERATOR".equals(user.getRole())) {
                throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "无权使用窗口智能问数");
            }
        }
    }

    private void validateWorkspacePermission(SysUser user, String workspace, Long counterId) {
        validateWorkspacePermission(user, workspace);
        if (!"counter".equals(workspace)) {
            return;
        }
        if ("SUPER_ADMIN".equals(user.getRole())) {
            return;
        }
        if (counterId == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "请选择窗口");
        }
        List<Long> ids = counterOperatorMapper.selectCounterIdsByUserId(user.getId());
        if (ids == null || !ids.contains(counterId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "无权访问该窗口");
        }
    }

    private void validateWorkspacePermission(SysUser user, String workspace, AiAskRequest req) {
        validateWorkspacePermission(user, workspace, req == null ? null : req.getCounterId());
    }

    private QueryScope resolveScope(SysUser user, String workspace, AiAskRequest req) {
        if ("admin".equals(workspace)) {
            Long rid = resolveAdminRegionId(user, req == null ? null : req.getRegionId());
            return new QueryScope(workspace, rid, null, req.getBusinessTypeId() == null ? null : List.of(req.getBusinessTypeId()), false);
        }
        validateWorkspacePermission(user, workspace, req);
        Counter counter = counterMapper.selectById(req.getCounterId());
        if (counter == null || counter.getDeleted() != null && counter.getDeleted() == 1) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), "窗口不存在");
        }
        List<Long> bizIds = counterBusinessMapper.selectBusinessTypeIdsByCounterId(counter.getId());
        if (req.getBusinessTypeId() != null) {
            bizIds = bizIds == null ? new ArrayList<>() : bizIds;
            if (!bizIds.contains(req.getBusinessTypeId())) {
                throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "该窗口不支持此业务类型");
            }
            bizIds = List.of(req.getBusinessTypeId());
        }
        return new QueryScope(workspace, counter.getRegionId(), counter.getId(), bizIds, true);
    }

    private Long resolveAdminRegionId(SysUser user, Long requestedRegionId) {
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "未登录");
        }
        if ("SUPER_ADMIN".equals(user.getRole())) {
            return requestedRegionId;
        }
        if (!"REGION_ADMIN".equals(user.getRole())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "无权使用智能问数");
        }

        Set<Long> allowed = new HashSet<>();
        List<Long> scopedRoots = sysUserMapper.selectRegionScopeIds(user.getId());
        if (scopedRoots != null && !scopedRoots.isEmpty()) {
            for (Long rid : scopedRoots) {
                if (rid == null) continue;
                List<Long> ids = regionMapper.selectDescendantIds(rid);
                if (ids != null) {
                    allowed.addAll(ids);
                }
            }
        } else if (user.getRegionId() != null) {
            List<Long> ids = regionMapper.selectDescendantIds(user.getRegionId());
            if (ids != null) {
                allowed.addAll(ids);
            }
        }

        if (allowed.isEmpty()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "未配置可用区域范围");
        }

        Long effective = requestedRegionId;
        if (effective == null) {
            if (scopedRoots != null && !scopedRoots.isEmpty()) {
                effective = scopedRoots.get(0);
            } else {
                effective = user.getRegionId();
            }
        }
        if (effective == null || !allowed.contains(effective)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "无权访问该区域");
        }
        return effective;
    }

    private void persistConversation(Long userId, String workspace, String sessionId, String question, AiAskResponse resp) {
        LocalDateTime now = LocalDateTime.now();
        AiSessionDTO session = (AiSessionDTO) redisTemplate.opsForHash().get(sessionIndexKey(userId, workspace), sessionId);
        if (session == null) {
            session = new AiSessionDTO();
            session.setId(sessionId);
            session.setWorkspace(workspace);
            session.setTitle(trimTitle(question));
            session.setCreatedAt(now);
        }
        session.setUpdatedAt(now);
        redisTemplate.opsForHash().put(sessionIndexKey(userId, workspace), sessionId, session);

        AiMessageDTO userMsg = new AiMessageDTO();
        userMsg.setId(UUID.randomUUID().toString().replace("-", ""));
        userMsg.setRole("user");
        userMsg.setText(question);
        userMsg.setCreatedAt(now);
        redisTemplate.opsForList().rightPush(messagesKey(userId, workspace, sessionId), userMsg);

        AiMessageDTO botMsg = new AiMessageDTO();
        botMsg.setId(UUID.randomUUID().toString().replace("-", ""));
        botMsg.setRole("assistant");
        botMsg.setText(resp.getAnswer());
        botMsg.setCreatedAt(now);
        redisTemplate.opsForList().rightPush(messagesKey(userId, workspace, sessionId), botMsg);

        if (resp.getTableColumns() != null && resp.getTableRows() != null) {
            Map<String, Object> last = new HashMap<>();
            last.put("answer", resp.getAnswer());
            last.put("columns", resp.getTableColumns());
            last.put("rows", resp.getTableRows());
            last.put("updatedAt", now);
            redisTemplate.opsForValue().set(lastKey(userId, workspace, sessionId), last);
        }
    }

    private String trimTitle(String question) {
        String q = question == null ? "" : question.trim();
        if (q.length() <= 18) {
            return q;
        }
        return q.substring(0, 18) + "...";
    }

    private String sessionIndexKey(Long userId, String workspace) {
        return "ai:sessions:" + userId + ":" + workspace;
    }

    private String messagesKey(Long userId, String workspace, String sessionId) {
        return "ai:messages:" + userId + ":" + workspace + ":" + sessionId;
    }

    private String lastKey(Long userId, String workspace, String sessionId) {
        return "ai:last:" + userId + ":" + workspace + ":" + sessionId;
    }

    private Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<Long, String> loadBusinessTypeNames(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        List<BusinessType> list = businessTypeMapper.selectBatchIds(ids);
        Map<Long, String> map = new HashMap<>();
        for (BusinessType bt : list) {
            map.put(bt.getId(), bt.getName());
        }
        return map;
    }

    private Map<Long, String> loadRegionNames(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, String> map = new HashMap<>();
        for (Long id : ids) {
            String name = regionMapper.selectFullRegionPath(id);
            if (name != null) {
                map.put(id, name);
            }
        }
        return map;
    }

    private int readIntConfig(String key, int defaultValue) {
        try {
            var hit = sysConfigMapper.selectOne(new LambdaQueryWrapper<com.queue.entity.SysConfig>().eq(com.queue.entity.SysConfig::getConfigKey, key));
            if (hit == null || hit.getConfigValue() == null || hit.getConfigValue().isBlank()) {
                return defaultValue;
            }
            return Integer.parseInt(hit.getConfigValue().trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
