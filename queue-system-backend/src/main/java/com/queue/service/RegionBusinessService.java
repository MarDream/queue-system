package com.queue.service;

import com.queue.dto.RegionBusinessDTO;
import com.queue.entity.BusinessType;

import java.util.List;

public interface RegionBusinessService {
    /**
     * 获取区域关联的所有业务类型（包含业务类型详情）
     */
    List<BusinessType> listBusinessTypesByRegion(Long regionId);

    /**
     * 获取区域关联的已启用业务类型
     */
    List<BusinessType> listEnabledBusinessTypesByRegion(Long regionId);

    /**
     * 获取所有可关联的业务类型（全局启用且未在该区域关联的）
     */
    List<BusinessType> listAvailableBusinessTypes(Long regionId);

    /**
     * 关联业务类型到区域
     */
    void linkBusinessType(RegionBusinessDTO dto);

    /**
     * 批量关联业务类型到区域
     */
    void batchLinkBusinessTypes(Long regionId, List<Long> businessTypeIds);

    /**
     * 取消关联
     */
    void unlinkBusinessType(Long regionId, Long businessTypeId);

    /**
     * 更新区域级别的业务启用状态
     */
    void updateRegionBusinessStatus(Long regionId, Long businessTypeId, Boolean isEnabled);

    /**
     * 更新区域内业务排序
     */
    void updateSortOrder(Long regionId, Long businessTypeId, Integer sortOrder);
}
