package com.queue.dto;

import lombok.Data;

@Data
public class RegionPageRequest {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private Long cityId;
    private String level;
    private String keyword;
}
