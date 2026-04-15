package com.queue.controller;

import com.queue.common.Result;
import com.queue.dto.ActiveTicketResponse;
import com.queue.dto.CancelTicketRequest;
import com.queue.dto.MyTicketVO;
import com.queue.dto.QueueStatusResponse;
import com.queue.dto.TakeTicketRequest;
import com.queue.dto.TakeTicketResponse;
import com.queue.entity.Region;
import com.queue.service.RegionService;
import com.queue.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class TicketController {

    private final TicketService ticketService;
    private final RegionService regionService;

    public TicketController(TicketService ticketService, RegionService regionService) {
        this.ticketService = ticketService;
        this.regionService = regionService;
    }

    @PostMapping("/ticket/take")
    public Result<TakeTicketResponse> takeTicket(@Valid @RequestBody TakeTicketRequest request) {
        return Result.ok(ticketService.takeTicket(request));
    }

    @GetMapping("/queue/status")
    public Result<QueueStatusResponse> getStatus(@RequestParam String ticketNo) {
        return Result.ok(ticketService.getQueueStatus(ticketNo));
    }

    @PostMapping("/ticket/cancel")
    public Result<Void> cancelTicket(@Valid @RequestBody CancelTicketRequest request) {
        ticketService.cancelTicket(request);
        return Result.ok();
    }

    @GetMapping("/ticket/my")
    public Result<List<MyTicketVO>> getMyTickets(@RequestParam String phone) {
        return Result.ok(ticketService.getMyTickets(phone));
    }

    /**
     * Check if user has an active (unfinished) ticket in the specified region.
     * Returns ticket details with queue progress if found, otherwise returns hasActive=false.
     */
    @PostMapping("/ticket/active")
    public Result<ActiveTicketResponse> getActiveTicket(@RequestParam String regionCode,
                                                        @RequestParam String phone) {
        Region region = regionService.getByCode(regionCode);
        if (region == null) {
            return Result.error(400, "区域不存在");
        }
        return Result.ok(ticketService.getActiveTicket(region.getId(), phone));
    }
}
