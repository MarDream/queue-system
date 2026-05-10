package com.queue.service;

import java.util.List;

public interface QueueService {
    long generateSequence(Long regionId, Long businessTypeId);
    void enqueue(Long regionId, Long businessTypeId, Long ticketId);
    void enqueueAtFront(Long regionId, Long businessTypeId, Long ticketId);
    void dequeue(Long regionId, Long businessTypeId, Long ticketId);
    long getWaitingCount(Long regionId, Long businessTypeId);
    void decrementWaitingCount(Long regionId, Long businessTypeId);
    void incrementWaitingCount(Long regionId, Long businessTypeId);
    Long peekNextTicketId(Long regionId, Long businessTypeId);
    boolean acquireLock(String lockKey, long ttlSeconds);
    void releaseLock(String lockKey);
    void pushCompletedHistory(Long counterId, String json);
    List<String> getCompletedHistory(Long counterId);

    /**
     * 从数据库修正所有业务类型的 Redis 等待计数缓存
     */
    void reconcileWaitingCounts();
}
