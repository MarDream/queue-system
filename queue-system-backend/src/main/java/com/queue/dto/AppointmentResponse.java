package com.queue.dto;

import lombok.Data;

@Data
public class AppointmentResponse {
    private Long id;
    private String appointmentNo;
    private String businessType;
    private String phone;
    private String name;
    private String appointmentDate;
    private String timeSlot;
    private String status;
}
