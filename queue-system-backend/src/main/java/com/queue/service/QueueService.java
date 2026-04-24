package com.queue.service;

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
}
