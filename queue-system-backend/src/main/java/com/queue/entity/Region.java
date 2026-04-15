package com.queue.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("region")
public class Region {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("region_name")
    @JsonProperty("name")
    private String regionName;
    private String level;
    private Long parentId;

    @TableField("region_code")
    @JsonProperty("code")
    private String regionCode;
    private Integer sortOrder;

    @TableField("announcement_text")
    @JsonProperty("announcementText")
    private String announcementText;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    @JsonProperty("parentName")
    private String parentName;
}
