package com.queue.service;

import com.queue.dto.AppointmentRequest;
import com.queue.dto.AppointmentResponse;

import java.util.List;

public interface AppointmentService {

    List<AppointmentResponse> listAppointments(AppointmentRequest request);

    AppointmentResponse createAppointment(AppointmentRequest request);

    void cancelAppointment(Long id);
}
