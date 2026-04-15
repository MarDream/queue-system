package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.queue.common.BusinessException;
import com.queue.dto.RegionBusinessDTO;
import com.queue.entity.BusinessType;
import com.queue.entity.Region;
import com.queue.entity.RegionBusiness;
import com.queue.mapper.BusinessTypeMapper;
import com.queue.mapper.RegionBusinessMapper;
import com.queue.mapper.RegionMapper;
import com.queue.service.RegionBusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegionBusinessServiceImpl implements RegionBusinessService {

    private final RegionBusinessMapper regionBusinessMapper;
    private final BusinessTypeMapper businessTypeMapper;
    private final RegionMapper regionMapper;

    @Override
    public List<BusinessType> listBusinessTypesByRegion(Long regionId) {
        List<RegionBusiness> rbList = regionBusinessMapper.selectList(
                new LambdaQueryWrapper<RegionBusiness>()
                        .eq(RegionBusiness::getRegionId, regionId)
                        .orderByAsc(RegionBusiness::getSortOrder)
        );
        if (rbList.isEmpty()) return new ArrayList<>();

        List<Long> btIds = rbList.stream().map(RegionBusiness::getBusinessTypeId).collect(Collectors.toList());
        return businessTypeMapper.selectBatchIds(btIds);
    }

    @Override
    public List<BusinessType> listEnabledBusinessTypesByRegion(Long regionId) {
        List<RegionBusiness> rbList = regionBusinessMapper.selectList(
                new LambdaQueryWrapper<RegionBusiness>()
                        .eq(RegionBusiness::getRegionId, regionId)
                        .eq(RegionBusiness::getIsEnabled, true)
                        .orderByAsc(RegionBusiness::getSortOrder)
        );
        if (rbList.isEmpty()) return new ArrayList<>();

        List<Long> btIds = rbList.stream().map(RegionBusiness::getBusinessTypeId).collect(Collectors.toList());
        // 同时要求全局启用
        return businessTypeMapper.selectList(
                new LambdaQueryWrapper<BusinessType>()
                        .in(BusinessType::getId, btIds)
                        .eq(BusinessType::getIsEnabled, true)
                        .orderByAsc(BusinessType::getSortOrder)
        );
    }

    @Override
    public List<BusinessType> listAvailableBusinessTypes(Long regionId) {
        // 获取该区域已关联的业务类型ID
        List<RegionBusiness> rbList = regionBusinessMapper.selectList(
                new LambdaQueryWrapper<RegionBusiness>()
                        .eq(RegionBusiness::getRegionId, regionId)
        );
        Set<Long> linkedIds = rbList.stream().map(RegionBusiness::getBusinessTypeId).collect(Collectors.toSet());

        // 获取父区域已关联的业务类型ID集合（用于限制子区域可选范围）
        Set<Long> parentScopeIds = getParentBusinessTypeIds(regionId);

        // 返回全局启用且未关联且在父区域范围内的业务类型
        List<BusinessType> allEnabled = businessTypeMapper.selectList(
                new LambdaQueryWrapper<BusinessType>()
                        .eq(BusinessType::getIsEnabled, true)
                        .eq(BusinessType::getDeleted, 0)
                        .orderByAsc(BusinessType::getSortOrder)
        );
        return allEnabled.stream()
                .filter(bt -> !linkedIds.contains(bt.getId()))
                .filter(bt -> parentScopeIds == null || parentScopeIds.contains(bt.getId()))
                .collect(Collectors.toList());
    }

    /**
     * 获取父区域已关联的业务类型ID集合
     * @return 如果无父区域则返回null（表示不受限），否则返回父区域关联的BT ID集合
     */
    private Set<Long> getParentBusinessTypeIds(Long regionId) {
        Region region = regionMapper.selectById(regionId);
        if (region == null || region.getParentId() == null) {
            return null; // 顶级区域，不受限
        }

        List<RegionBusiness> parentRbList = regionBusinessMapper.selectList(
                new LambdaQueryWrapper<RegionBusiness>()
                        .eq(RegionBusiness::getRegionId, region.getParentId())
        );
        return parentRbList.stream()
                .map(RegionBusiness::getBusinessTypeId)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void linkBusinessType(RegionBusinessDTO dto) {
        // 检查业务类型全局是否启用
        BusinessType bt = businessTypeMapper.selectById(dto.getBusinessTypeId());
        if (bt == null || !Boolean.TRUE.equals(bt.getIsEnabled())) {
            throw new BusinessException(50001, "该业务类型未启用或不存在");
        }

        // 检查该业务类型是否在父区域已关联范围内
        Set<Long> parentScopeIds = getParentBusinessTypeIds(dto.getRegionId());
        if (parentScopeIds != null && !parentScopeIds.contains(dto.getBusinessTypeId())) {
            Region region = regionMapper.selectById(dto.getRegionId());
            String regionName = region != null ? region.getRegionName() : "当前区域";
            throw new BusinessException(50001, "该业务类型不在父区域已关联的业务范围内，无法关联到「" + regionName + "」");
        }

        // 检查是否已关联
        RegionBusiness existing = regionBusinessMapper.selectOne(
                new LambdaQueryWrapper<RegionBusiness>()
                        .eq(RegionBusiness::getRegionId, dto.getRegionId())
                        .eq(RegionBusiness::getBusinessTypeId, dto.getBusinessTypeId())
        );
        if (existing != null) {
            throw new BusinessException(50001, "该业务类型已关联到当前区域");
        }

        RegionBusiness rb = new RegionBusiness();
        rb.setRegionId(dto.getRegionId());
        rb.setBusinessTypeId(dto.getBusinessTypeId());
        rb.setIsEnabled(dto.getIsEnabled() != null ? dto.getIsEnabled() : true);
        rb.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        regionBusinessMapper.insert(rb);
    }

    @Override
    @Transactional
    public void batchLinkBusinessTypes(Long regionId, List<Long> businessTypeIds) {
        // 提前检查父区域范围，避免部分成功部分失败
        Set<Long> parentScopeIds = getParentBusinessTypeIds(regionId);
        Region region = regionMapper.selectById(regionId);
        String regionName = region != null ? region.getRegionName() : "当前区域";

        for (Long btId : businessTypeIds) {
            // 检查是否在父区域范围内
            if (parentScopeIds != null && !parentScopeIds.contains(btId)) {
                throw new BusinessException(50001, "业务类型ID " + btId + " 不在父区域已关联的业务范围内，无法关联到「" + regionName + "」");
            }
            try {
                RegionBusinessDTO dto = new RegionBusinessDTO();
                dto.setRegionId(regionId);
                dto.setBusinessTypeId(btId);
                dto.setIsEnabled(true);
                linkBusinessType(dto);
            } catch (BusinessException e) {
                // 跳过已关联的
            }
        }
    }

    @Override
    @Transactional
    public void unlinkBusinessType(Long regionId, Long businessTypeId) {
        regionBusinessMapper.delete(
                new LambdaQueryWrapper<RegionBusiness>()
                        .eq(RegionBusiness::getRegionId, regionId)
                        .eq(RegionBusiness::getBusinessTypeId, businessTypeId)
        );
    }

    @Override
    @Transactional
    public void updateRegionBusinessStatus(Long regionId, Long businessTypeId, Boolean isEnabled) {
        RegionBusiness rb = regionBusinessMapper.selectOne(
                new LambdaQueryWrapper<RegionBusiness>()
                        .eq(RegionBusiness::getRegionId, regionId)
                        .eq(RegionBusiness::getBusinessTypeId, businessTypeId)
        );
        if (rb == null) {
            throw new BusinessException(50001, "该业务类型未关联到当前区域");
        }
        rb.setIsEnabled(isEnabled);
        regionBusinessMapper.updateById(rb);
    }

    @Override
    @Transactional
    public void updateSortOrder(Long regionId, Long businessTypeId, Integer sortOrder) {
        RegionBusiness rb = regionBusinessMapper.selectOne(
                new LambdaQueryWrapper<RegionBusiness>()
                        .eq(RegionBusiness::getRegionId, regionId)
                        .eq(RegionBusiness::getBusinessTypeId, businessTypeId)
        );
        if (rb == null) {
            throw new BusinessException(50001, "该业务类型未关联到当前区域");
        }
        rb.setSortOrder(sortOrder);
        regionBusinessMapper.updateById(rb);
    }
}
