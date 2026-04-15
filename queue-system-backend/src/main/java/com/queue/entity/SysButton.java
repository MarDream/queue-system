package com.queue.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_button")
public class SysButton {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long menuId;
    private String name;
    private String code;
    private Integer sortOrder;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
