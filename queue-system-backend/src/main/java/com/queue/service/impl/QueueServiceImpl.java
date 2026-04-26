package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.queue.entity.Ticket;
import com.queue.enums.TicketStatus;
import com.queue.mapper.TicketMapper;
import com.queue.service.QueueService;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class QueueServiceImpl implements QueueService {

    private final StringRedisTemplate stringRedisTemplate;
    private final TicketMapper ticketMapper;
    private final ThreadLocal<Map<String, String>> lockValueHolder = ThreadLocal.withInitial(HashMap::new);
    private static final Duration WAITING_COUNT_TTL = Duration.ofMinutes(10);

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) " +
        "else " +
            "return 0 " +
        "end",
        Long.class
    );

    private String seqKey(Long regionId, Long businessTypeId, String date) {
        return "queue:seq:" + regionId + ":" + businessTypeId + ":" + date;
    }

    private String waitingListKey(Long regionId, Long businessTypeId) {
        return "queue:waiting:" + regionId + ":" + businessTypeId;
    }

    private String waitingCountKey(Long regionId, Long businessTypeId) {
        return "queue:count:" + regionId + ":" + businessTypeId;
    }

    public QueueServiceImpl(StringRedisTemplate stringRedisTemplate, TicketMapper ticketMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.ticketMapper = ticketMapper;
    }

    @Override
    public long generateSequence(Long regionId, Long businessTypeId) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = seqKey(regionId, businessTypeId, date);

        Long seq = stringRedisTemplate.opsForValue().increment(key);
        if (seq != null && seq == 1L) {
            // 首次生成，设置过期时间
            stringRedisTemplate.expire(key, Duration.ofHours(48));
        }
        return seq != null ? seq : 1L;
    }

    @Override
    public void enqueue(Long regionId, Long businessTypeId, Long ticketId) {
        String key = waitingListKey(regionId, businessTypeId);
        stringRedisTemplate.opsForList().rightPush(key, ticketId.toString());
    }

    @Override
    public void enqueueAtFront(Long regionId, Long businessTypeId, Long ticketId) {
        String key = waitingListKey(regionId, businessTypeId);
        stringRedisTemplate.opsForList().leftPush(key, ticketId.toString());
    }

    @Override
    public void dequeue(Long regionId, Long businessTypeId, Long ticketId) {
        String key = waitingListKey(regionId, businessTypeId);
        stringRedisTemplate.opsForList().remove(key, 1, ticketId.toString());
    }

    @Override
    public long getWaitingCount(Long regionId, Long businessTypeId) {
        if (regionId != null) {
            String cached = stringRedisTemplate.opsForValue().get(waitingCountKey(regionId, businessTypeId));
            if (cached != null) {
                try {
                    return Long.parseLong(cached);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getBusinessTypeId, businessTypeId)
                .eq(Ticket::getStatus, TicketStatus.WAITING.getValue());
        if (regionId != null) {
            wrapper.eq(Ticket::getRegionId, regionId);
        }
        Long count = ticketMapper.selectCount(wrapper);
        long result = count != null ? count : 0L;
        if (regionId != null) {
            String key = waitingCountKey(regionId, businessTypeId);
            stringRedisTemplate.opsForValue().set(key, String.valueOf(result), WAITING_COUNT_TTL);
        }
        return result;
    }

    @Override
    public void decrementWaitingCount(Long regionId, Long businessTypeId) {
        if (regionId == null) return;
        String key = waitingCountKey(regionId, businessTypeId);
        Long val = stringRedisTemplate.opsForValue().decrement(key);
        if (val != null && val < 0) {
            stringRedisTemplate.opsForValue().set(key, "0", WAITING_COUNT_TTL);
            return;
        }
        stringRedisTemplate.expire(key, WAITING_COUNT_TTL);
    }

    @Override
    public void incrementWaitingCount(Long regionId, Long businessTypeId) {
        if (regionId == null) return;
        String key = waitingCountKey(regionId, businessTypeId);
        Long val = stringRedisTemplate.opsForValue().increment(key);
        if (val != null && val == 1L) {
            stringRedisTemplate.expire(key, WAITING_COUNT_TTL);
            return;
        }
        stringRedisTemplate.expire(key, WAITING_COUNT_TTL);
    }

    @Override
    public Long peekNextTicketId(Long regionId, Long businessTypeId) {
        String key = waitingListKey(regionId, businessTypeId);
        String val = stringRedisTemplate.opsForList().index(key, 0);
        return val != null ? Long.parseLong(val) : null;
    }

    @Override
    public boolean acquireLock(String lockKey, long ttlSeconds) {
        String lockValue = UUID.randomUUID().toString();
        try {
            Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(ttlSeconds));
            if (Boolean.TRUE.equals(result)) {
                lockValueHolder.get().put(lockKey, lockValue);
                return true;
            }
            return false;
        } catch (Exception e) {
            // 如果 Redis 操作失败，抛出异常而不是静默返回 false
            throw new RuntimeException("Failed to acquire lock: " + lockKey, e);
        }
    }

    @Override
    public void releaseLock(String lockKey) {
        Map<String, String> map = lockValueHolder.get();
        String lockValue = map.remove(lockKey);
        if (lockValue == null) {
            return;
        }
        try {
            stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), lockValue);
        } finally {
            if (map.isEmpty()) {
                lockValueHolder.remove();
            }
        }
    }
}
