package com.queue.controller;

import com.queue.dto.AppointmentRequest;
import com.queue.dto.AppointmentResponse;
import com.queue.common.Result;
import com.queue.service.AppointmentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/appointment")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/list")
    public Result<List<AppointmentResponse>> listAppointments(AppointmentRequest request) {
        return Result.ok(appointmentService.listAppointments(request));
    }

    @PostMapping("/create")
    public Result<AppointmentResponse> createAppointment(@RequestBody AppointmentRequest request) {
        AppointmentResponse response = appointmentService.createAppointment(request);
        return Result.ok(response);
    }

    @PostMapping("/{id}/cancel")
    public Result<Boolean> cancelAppointment(@PathVariable Long id) {
        appointmentService.cancelAppointment(id);
        return Result.ok(true);
    }
}
