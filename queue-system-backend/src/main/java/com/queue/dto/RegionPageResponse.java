package com.queue.dto;

import com.queue.entity.Region;
import lombok.Data;
import java.util.List;

@Data
public class RegionPageResponse {
    private List<Region> records;
    private long total;
    private int pageNum;
    private int pageSize;
}
