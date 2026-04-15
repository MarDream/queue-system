package com.queue.dto;

import lombok.Data;

@Data
public class RegionSortRequest {
    private Long id;
    private Integer sortOrder;
    private Long parentId;
}
