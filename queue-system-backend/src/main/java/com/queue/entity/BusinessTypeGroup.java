package com.queue.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("business_type_group")
public class BusinessTypeGroup {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;            // 分组名称
    private Integer sortOrder;      // 分组排序

    @TableField(exist = false)
    private List<Long> businessTypeIds;   // 纳入分组的业务类型ID

    @TableLogic
    private Integer deleted;        // 软删除

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
