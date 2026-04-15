package com.queue.service;

import com.queue.dto.RegionPageRequest;
import com.queue.dto.RegionSortRequest;
import com.queue.entity.Region;
import java.util.List;

public interface RegionService {
    List<Region> listByLevel(String level);
    List<Region> listByParentId(Long parentId);
    Region getById(Long id);
    Region getByCode(String code);
    String getFullRegionName(String code);
    Region create(Region region);
    Region update(Region region);
    void delete(Long id);
    List<Region> listAll();
    void batchUpdateSort(List<RegionSortRequest> requests);

    com.baomidou.mybatisplus.core.metadata.IPage<Region> listPage(RegionPageRequest request);

    /**
     * 获取指定区域及其所有子区域的ID列表（递归）
     */
    List<Long> getDescendantRegionIds(Long regionId);
}
