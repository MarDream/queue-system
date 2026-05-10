package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.queue.entity.Ticket;
import com.queue.enums.TicketStatus;
import com.queue.mapper.TicketMapper;
import com.queue.mapper.BusinessTypeMapper;
import com.queue.mapper.RegionMapper;
import com.queue.entity.BusinessType;
import com.queue.entity.Region;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.queue.service.QueueService;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class QueueServiceImpl implements QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueServiceImpl.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final TicketMapper ticketMapper;
    private final BusinessTypeMapper businessTypeMapper;
    private final RegionMapper regionMapper;
    private final ThreadLocal<Map<String, String>> lockValueHolder = ThreadLocal.withInitial(HashMap::new);
    private static final Duration WAITING_COUNT_TTL = Duration.ofMinutes(10);
    // Redis 降级时的本地互斥锁，按 lockKey 粒度加锁
    private final ConcurrentHashMap<String, ReentrantLock> fallbackLocks = new ConcurrentHashMap<>();

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

    private String completedHistoryKey(Long counterId) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "counter:completed:" + counterId + ":" + date;
    }

    public QueueServiceImpl(StringRedisTemplate stringRedisTemplate, TicketMapper ticketMapper,
                            BusinessTypeMapper businessTypeMapper, RegionMapper regionMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.ticketMapper = ticketMapper;
        this.businessTypeMapper = businessTypeMapper;
        this.regionMapper = regionMapper;
    }

    @Override
    public long generateSequence(Long regionId, Long businessTypeId) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = seqKey(regionId, businessTypeId, date);
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        Long currentMax = ticketMapper.selectMaxSequenceByRegionAndBusinessTypeId(
                regionId,
                businessTypeId,
                startOfDay,
                endOfDay
        );
        long dbFloor = currentMax != null && currentMax > 0 ? currentMax : 0L;
        long cachedFloor = 0L;
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                cachedFloor = Long.parseLong(cached);
            } catch (NumberFormatException ignored) {
                cachedFloor = 0L;
            }
        }

        long floor = Math.max(dbFloor, cachedFloor);
        if (cached == null || cachedFloor < floor) {
            stringRedisTemplate.opsForValue().set(key, String.valueOf(floor), Duration.ofHours(48));
        } else {
            stringRedisTemplate.expire(key, Duration.ofHours(48));
        }

        Long seq = stringRedisTemplate.opsForValue().increment(key);
        if (seq != null) {
            stringRedisTemplate.expire(key, Duration.ofHours(48));
            return seq;
        }

        return dbFloor + 1L;
    }

    @Override
    public void enqueue(Long regionId, Long businessTypeId, Long ticketId) {
        try {
            String key = waitingListKey(regionId, businessTypeId);
            stringRedisTemplate.opsForList().rightPush(key, ticketId.toString());
        } catch (Exception e) {
            // Redis 不可用时降级：票号已在 DB 中 status='waiting'，callNext() 会从 DB 查询
            log.warn("Redis enqueue failed, fallback to DB. regionId={}, typeId={}, ticketId={}", regionId, businessTypeId, ticketId, e);
        }
    }

    @Override
    public void enqueueAtFront(Long regionId, Long businessTypeId, Long ticketId) {
        try {
            String key = waitingListKey(regionId, businessTypeId);
            stringRedisTemplate.opsForList().leftPush(key, ticketId.toString());
        } catch (Exception e) {
            // Redis 不可用时降级：票号已在 DB 中 status='waiting'，callNext() 会从 DB 查询
            log.warn("Redis enqueueAtFront failed, fallback to DB. regionId={}, typeId={}, ticketId={}", regionId, businessTypeId, ticketId, e);
        }
    }

    @Override
    public void dequeue(Long regionId, Long businessTypeId, Long ticketId) {
        try {
            String key = waitingListKey(regionId, businessTypeId);
            stringRedisTemplate.opsForList().remove(key, 1, ticketId.toString());
        } catch (Exception e) {
            // Redis 不可用时降级：callNext() 已通过 DB 更新票号状态，Redis 队列不一致会在下次操作时自愈
            log.warn("Redis dequeue failed, fallback to DB. regionId={}, typeId={}, ticketId={}", regionId, businessTypeId, ticketId, e);
        }
    }

    @Override
    public long getWaitingCount(Long regionId, Long businessTypeId) {
        if (regionId != null) {
            String key = waitingCountKey(regionId, businessTypeId);
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                try {
                    long val = Long.parseLong(cached);
                    if (val >= 0) {
                        return val;
                    }
                    // 负数：缓存不准，从数据库修正
                    long dbCount = loadWaitingCountFromDb(regionId, businessTypeId);
                    stringRedisTemplate.opsForValue().set(key, String.valueOf(dbCount), WAITING_COUNT_TTL);
                    return dbCount;
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
        Boolean hasKey = stringRedisTemplate.hasKey(key);
        if (Boolean.FALSE.equals(hasKey)) {
            long dbCount = loadWaitingCountFromDb(regionId, businessTypeId);
            stringRedisTemplate.opsForValue().set(key, String.valueOf(dbCount), WAITING_COUNT_TTL);
            return;
        }
        Long val = stringRedisTemplate.opsForValue().decrement(key);
        if (val != null && val < 0) {
            // 缓存不准，从数据库修正
            long dbCount = loadWaitingCountFromDb(regionId, businessTypeId);
            stringRedisTemplate.opsForValue().set(key, String.valueOf(dbCount), WAITING_COUNT_TTL);
            return;
        }
        stringRedisTemplate.expire(key, WAITING_COUNT_TTL);
    }

    @Override
    public void incrementWaitingCount(Long regionId, Long businessTypeId) {
        if (regionId == null) return;
        String key = waitingCountKey(regionId, businessTypeId);
        Boolean hasKey = stringRedisTemplate.hasKey(key);
        if (Boolean.FALSE.equals(hasKey)) {
            long dbCount = loadWaitingCountFromDb(regionId, businessTypeId);
            stringRedisTemplate.opsForValue().set(key, String.valueOf(dbCount), WAITING_COUNT_TTL);
            return;
        }
        Long val = stringRedisTemplate.opsForValue().increment(key);
        if (val != null && val == 1L) {
            stringRedisTemplate.expire(key, WAITING_COUNT_TTL);
            return;
        }
        stringRedisTemplate.expire(key, WAITING_COUNT_TTL);
    }

    private long loadWaitingCountFromDb(Long regionId, Long businessTypeId) {
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getBusinessTypeId, businessTypeId)
                .eq(Ticket::getStatus, TicketStatus.WAITING.getValue())
                .eq(Ticket::getRegionId, regionId);
        Long count = ticketMapper.selectCount(wrapper);
        return count != null ? count : 0L;
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
            // Redis 不可用时降级：使用本地 ReentrantLock 保护并发
            log.warn("Redis acquireLock failed, fallback to local lock. lockKey={}", lockKey, e);
            ReentrantLock fallback = fallbackLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());
            boolean acquired = fallback.tryLock();
            if (acquired) {
                // 标记为本地锁，releaseLock 时走本地解锁逻辑
                lockValueHolder.get().put(lockKey, "LOCAL:" + lockValue);
            }
            return acquired;
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
            if (lockValue.startsWith("LOCAL:")) {
                // 本地降级锁：释放 ReentrantLock
                ReentrantLock fallback = fallbackLocks.get(lockKey);
                if (fallback != null && fallback.isHeldByCurrentThread()) {
                    fallback.unlock();
                }
            } else {
                stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), lockValue);
            }
        } finally {
            if (map.isEmpty()) {
                lockValueHolder.remove();
            }
        }
    }

    @Override
    public void pushCompletedHistory(Long counterId, String json) {
        String key = completedHistoryKey(counterId);
        stringRedisTemplate.opsForList().leftPush(key, json);
        // 自动过期到次日凌晨
        stringRedisTemplate.expireAt(key, LocalDate.now().plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.ofHours(8)));
    }

    @Override
    public List<String> getCompletedHistory(Long counterId) {
        String key = completedHistoryKey(counterId);
        List<String> list = stringRedisTemplate.opsForList().range(key, 0, -1);
        return list != null ? list : List.of();
    }

    @Override
    public void reconcileWaitingCounts() {
        List<BusinessType> types = businessTypeMapper.selectList(
                new QueryWrapper<BusinessType>().eq("is_enabled", 1));
        List<Region> regions = regionMapper.selectList(null);

        int corrected = 0;
        for (Region region : regions) {
            for (BusinessType type : types) {
                try {
                    long dbCount = loadWaitingCountFromDb(region.getId(), type.getId());
                    String key = waitingCountKey(region.getId(), type.getId());
                    String cached = stringRedisTemplate.opsForValue().get(key);
                    if (cached != null) {
                        long cachedVal = Long.parseLong(cached);
                        if (cachedVal != dbCount) {
                            stringRedisTemplate.opsForValue().set(key, String.valueOf(dbCount), WAITING_COUNT_TTL);
                            corrected++;
                            log.info("Reconciled waiting count: region={}, type={}, cached={}, actual={}",
                                    region.getId(), type.getId(), cachedVal, dbCount);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Reconcile failed for region={}, type={}", region.getId(), type.getId(), e);
                }
            }
        }
        if (corrected > 0) {
            log.info("Waiting count reconciliation completed: {} entries corrected", corrected);
        }
    }
}
