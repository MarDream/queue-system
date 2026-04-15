package com.queue.controller;

import com.queue.common.Result;
import com.queue.dto.RegionBusinessDTO;
import com.queue.entity.BusinessType;
import com.queue.service.RegionBusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionBusinessController {

    private final RegionBusinessService regionBusinessService;

    /**
     * 获取区域关联的业务类型列表
     */
    @GetMapping("/{regionId}/business-types")
    public Result<List<BusinessType>> listRegionBusiness(@PathVariable Long regionId) {
        return Result.ok(regionBusinessService.listBusinessTypesByRegion(regionId));
    }

    /**
     * 获取可关联的业务类型（全局启用且未关联的）
     */
    @GetMapping("/{regionId}/business-types/available")
    public Result<List<BusinessType>> listAvailableBusinessTypes(@PathVariable Long regionId) {
        return Result.ok(regionBusinessService.listAvailableBusinessTypes(regionId));
    }

    /**
     * 关联业务类型到区域
     */
    @PostMapping("/{regionId}/business-types")
    public Result<Void> linkBusinessType(@PathVariable Long regionId, @RequestBody RegionBusinessDTO dto) {
        dto.setRegionId(regionId);
        regionBusinessService.linkBusinessType(dto);
        return Result.ok();
    }

    /**
     * 批量关联业务类型
     */
    @PostMapping("/{regionId}/business-types/batch")
    public Result<Void> batchLinkBusinessTypes(@PathVariable Long regionId, @RequestBody List<Long> businessTypeIds) {
        regionBusinessService.batchLinkBusinessTypes(regionId, businessTypeIds);
        return Result.ok();
    }

    /**
     * 取消关联
     */
    @DeleteMapping("/{regionId}/business-types/{businessTypeId}")
    public Result<Void> unlinkBusinessType(@PathVariable Long regionId, @PathVariable Long businessTypeId) {
        regionBusinessService.unlinkBusinessType(regionId, businessTypeId);
        return Result.ok();
    }

    /**
     * 更新区域级别业务启用状态
     */
    @PutMapping("/{regionId}/business-types/{businessTypeId}/status")
    public Result<Void> updateBusinessStatus(@PathVariable Long regionId, @PathVariable Long businessTypeId, @RequestBody RegionBusinessDTO dto) {
        regionBusinessService.updateRegionBusinessStatus(regionId, businessTypeId, dto.getIsEnabled());
        return Result.ok();
    }
}
