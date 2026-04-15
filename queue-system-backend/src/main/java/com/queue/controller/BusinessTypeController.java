package com.queue.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.queue.common.Result;
import com.queue.dto.BusinessTypeDTO;
import com.queue.entity.BusinessType;
import com.queue.entity.RegionBusiness;
import com.queue.mapper.BusinessTypeMapper;
import com.queue.mapper.RegionBusinessMapper;
import com.queue.service.QueueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class BusinessTypeController {

    private final BusinessTypeMapper businessTypeMapper;
    private final RegionBusinessMapper regionBusinessMapper;
    private final QueueService queueService;

    public BusinessTypeController(BusinessTypeMapper businessTypeMapper,
                                  RegionBusinessMapper regionBusinessMapper,
                                  QueueService queueService) {
        this.businessTypeMapper = businessTypeMapper;
        this.regionBusinessMapper = regionBusinessMapper;
        this.queueService = queueService;
    }

    @GetMapping("/business-types")
    public Result<List<BusinessTypeDTO>> list(@RequestParam(required = false) Long regionId) {
        List<BusinessType> types;
        if (regionId != null) {
            // 查询该区域关联且启用的业务类型（同时要求全局启用）
            List<RegionBusiness> rbList = regionBusinessMapper.selectList(
                new QueryWrapper<RegionBusiness>()
                    .eq("region_id", regionId)
                    .eq("is_enabled", 1)
                    .orderByAsc("sort_order")
            );
            if (rbList.isEmpty()) {
                types = Collections.emptyList();
            } else {
                List<Long> btIds = rbList.stream().map(RegionBusiness::getBusinessTypeId).collect(Collectors.toList());
                types = businessTypeMapper.selectList(
                    new QueryWrapper<BusinessType>()
                        .in("id", btIds)
                        .eq("is_enabled", 1)
                        .eq("deleted", 0)
                        .orderByAsc("sort_order")
                );
            }
        } else {
            types = businessTypeMapper.selectList(
                new QueryWrapper<BusinessType>()
                    .eq("is_enabled", 1)
                    .eq("deleted", 0)
                    .orderByAsc("sort_order")
            );
        }

        List<BusinessTypeDTO> dtos = types.stream().map(bt -> {
            BusinessTypeDTO dto = new BusinessTypeDTO();
            dto.setId(bt.getId());
            dto.setName(bt.getName());
            dto.setPrefix(bt.getPrefix());
            dto.setDescription(bt.getDescription());
            // waitingCount 按 regionId 过滤（regionId 为 null 时全局统计）
            dto.setWaitingCount((int) queueService.getWaitingCount(regionId, bt.getId()));
            return dto;
        }).collect(Collectors.toList());
        return Result.ok(dtos);
    }
}
