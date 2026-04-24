package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.queue.entity.Ticket;
import com.queue.enums.TicketStatus;
import com.queue.mapper.TicketMapper;
import com.queue.service.QueueService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class QueueServiceImpl implements QueueService {

    private final StringRedisTemplate stringRedisTemplate;
    private final TicketMapper ticketMapper;
    private final ThreadLocal<String> lockValueHolder = new ThreadLocal<>();

    public QueueServiceImpl(StringRedisTemplate stringRedisTemplate, TicketMapper ticketMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.ticketMapper = ticketMapper;
    }

    @Override
    public long generateSequence(Long regionId, Long businessTypeId) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = "queue:seq:" + regionId + ":" + businessTypeId + ":" + date;

        Long seq = stringRedisTemplate.opsForValue().increment(key);
        if (seq != null && seq == 1L) {
            // 首次生成，设置过期时间
            stringRedisTemplate.expire(key, Duration.ofHours(48));
        }
        return seq != null ? seq : 1L;
    }

    @Override
    public void enqueue(Long regionId, Long businessTypeId, Long ticketId) {
        String key = "queue:waiting:" + regionId + ":" + businessTypeId;
        stringRedisTemplate.opsForList().rightPush(key, ticketId.toString());
    }

    @Override
    public void enqueueAtFront(Long regionId, Long businessTypeId, Long ticketId) {
        String key = "queue:waiting:" + regionId + ":" + businessTypeId;
        stringRedisTemplate.opsForList().leftPush(key, ticketId.toString());
    }

    @Override
    public void dequeue(Long regionId, Long businessTypeId, Long ticketId) {
        String key = "queue:waiting:" + regionId + ":" + businessTypeId;
        stringRedisTemplate.opsForList().remove(key, 1, ticketId.toString());
    }

    @Override
    public long getWaitingCount(Long regionId, Long businessTypeId) {
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getBusinessTypeId, businessTypeId)
                .eq(Ticket::getStatus, TicketStatus.WAITING.getValue());
        if (regionId != null) {
            wrapper.eq(Ticket::getRegionId, regionId);
        }
        Long count = ticketMapper.selectCount(wrapper);
        return count != null ? count : 0L;
    }

    @Override
    public void decrementWaitingCount(Long regionId, Long businessTypeId) {
        String key = "queue:count:" + regionId + ":" + businessTypeId;
        Long current = getWaitingCount(regionId, businessTypeId);
        if (current > 0) {
            stringRedisTemplate.opsForValue().decrement(key);
        }
    }

    @Override
    public void incrementWaitingCount(Long regionId, Long businessTypeId) {
        String key = "queue:count:" + regionId + ":" + businessTypeId;
        stringRedisTemplate.opsForValue().increment(key);
    }

    @Override
    public Long peekNextTicketId(Long regionId, Long businessTypeId) {
        String key = "queue:waiting:" + regionId + ":" + businessTypeId;
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
                lockValueHolder.set(lockValue);
                return true;
            }
            // 如果返回 false 或 null，尝试删除可能存在的过期锁后重试
            stringRedisTemplate.delete(lockKey);
            result = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(ttlSeconds));
            if (Boolean.TRUE.equals(result)) {
                lockValueHolder.set(lockValue);
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
        String storedValue = lockValueHolder.get();
        if (storedValue != null && storedValue.equals(stringRedisTemplate.opsForValue().get(lockKey))) {
            stringRedisTemplate.delete(lockKey);
        }
        lockValueHolder.remove();
    }
}
