package com.queue.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("appointment")
public class Appointment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long businessTypeId;
    private String phone;
    private String name;
    @TableField("appointment_date")
    private LocalDate date;
    private String timeSlot;
    private String status;
    private Long ticketId;
    private LocalDateTime createdAt;
}
