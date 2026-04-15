package com.queue.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("region_business")
public class RegionBusiness {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long regionId;
    private Long businessTypeId;
    private Boolean isEnabled;          // 该区域是否启用此业务
    private Integer sortOrder;          // 该区域内的排序

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
