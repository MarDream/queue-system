package com.queue.controller;

import com.queue.common.Result;
import com.queue.dto.CounterCallResponse;
import com.queue.dto.CounterOperationRequest;
import com.queue.service.CounterService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/counter")
public class CounterController {

    private final CounterService counterService;

    public CounterController(CounterService counterService) {
        this.counterService = counterService;
    }

    @PostMapping("/call/next")
    public Result<CounterCallResponse> callNext(@RequestBody CounterOperationRequest request) {
        CounterCallResponse result = counterService.callNext(request.getCounterId());
        return Result.ok(result, result == null ? "暂无等待客户" : null);
    }

    @PostMapping("/call/recall")
    public Result<CounterCallResponse> recall(@RequestBody CounterOperationRequest request) {
        return Result.ok(counterService.recall(request.getCounterId()));
    }

    @PostMapping("/call/skip")
    public Result<Void> skip(@RequestBody CounterOperationRequest request) {
        counterService.skip(request.getCounterId());
        return Result.ok();
    }

    @PostMapping("/serve")
    public Result<Void> serve(@RequestBody CounterOperationRequest request) {
        counterService.serve(request.getCounterId());
        return Result.ok();
    }

    @PostMapping("/complete")
    public Result<Void> complete(@RequestBody CounterOperationRequest request) {
        counterService.complete(request.getCounterId());
        return Result.ok();
    }

    @PostMapping("/toggle-pause")
    public Result<Void> togglePause(@RequestBody CounterOperationRequest request) {
        counterService.togglePause(request.getCounterId());
        return Result.ok();
    }

    @PostMapping("/reactivate")
    public Result<Void> reactivate(@RequestBody ReactivateRequest request) {
        counterService.reactivateSkippedTicket(request.getTicketNo());
        return Result.ok();
    }

    public static class ReactivateRequest {
        private String ticketNo;

        public String getTicketNo() {
            return ticketNo;
        }

        public void setTicketNo(String ticketNo) {
            this.ticketNo = ticketNo;
        }
    }
}
