package com.queue.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("business_type")
public class BusinessType {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;                // 业务类型名称（全局唯一）
    private String prefix;              // 票号前缀
    private String description;         // 描述
    private Integer dailyAppointmentLimit;  // 每日预约限额
    private Boolean isEnabled;          // 全局是否启用
    private Integer sortOrder;          // 显示排序
    @Version
    private Integer version;            // 乐观锁
    @TableLogic
    private Integer deleted;            // 软删除
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
