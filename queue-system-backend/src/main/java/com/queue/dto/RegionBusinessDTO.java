package com.queue.dto;

import lombok.Data;

@Data
public class RegionBusinessDTO {
    private Long regionId;
    private Long businessTypeId;
    private Boolean isEnabled;
    private Integer sortOrder;
}
