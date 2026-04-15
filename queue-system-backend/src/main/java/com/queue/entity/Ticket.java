package com.queue.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ticket")
public class Ticket {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long regionId;             // 所属区域ID
    private String ticketNo;
    private Long businessTypeId;
    private String source;
    private String phone;
    private String name;
    private String status;
    private Long counterId;
    private LocalDateTime calledAt;
    private LocalDateTime servedAt;
    private LocalDateTime completedAt;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
