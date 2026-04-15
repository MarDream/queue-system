package com.queue.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class AppointmentRequest {
    private Long businessTypeId;
    private String phone;
    private String name;
    private LocalDate appointmentDate;
    private String timeSlot; // morning/afternoon
}
