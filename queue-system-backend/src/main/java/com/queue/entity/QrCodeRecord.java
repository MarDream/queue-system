package com.queue.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("qrcode_record")
public class QrCodeRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long regionId;
    private String regionCode;         // 冗余，方便查询
    private String regionName;
    private String url;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    @Version
    private Integer version;
}
