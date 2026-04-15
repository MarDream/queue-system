package com.queue.service;

import com.queue.dto.CounterCallResponse;

public interface CounterService {
    CounterCallResponse callNext(Long counterId);
    CounterCallResponse recall(Long counterId);
    void skip(Long counterId);
    void serve(Long counterId);
    void complete(Long counterId);
    void togglePause(Long counterId);
}
