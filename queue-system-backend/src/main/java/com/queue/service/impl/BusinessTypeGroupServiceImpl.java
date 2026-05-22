package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.queue.common.BusinessException;
import com.queue.entity.BusinessType;
import com.queue.entity.BusinessTypeGroup;
import com.queue.mapper.BusinessTypeGroupMapper;
import com.queue.mapper.BusinessTypeMapper;
import com.queue.service.BusinessTypeGroupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BusinessTypeGroupServiceImpl implements BusinessTypeGroupService {

    private final BusinessTypeGroupMapper businessTypeGroupMapper;
    private final BusinessTypeMapper businessTypeMapper;

    public BusinessTypeGroupServiceImpl(BusinessTypeGroupMapper businessTypeGroupMapper,
                                        BusinessTypeMapper businessTypeMapper) {
        this.businessTypeGroupMapper = businessTypeGroupMapper;
        this.businessTypeMapper = businessTypeMapper;
    }

    @Override
    public List<BusinessTypeGroup> listAll() {
        return businessTypeGroupMapper.selectList(
                new QueryWrapper<BusinessTypeGroup>()
                        .eq("deleted", 0)
                        .orderByAsc("sort_order", "id")
        );
    }

    @Override
    @Transactional
    public BusinessTypeGroup create(BusinessTypeGroup group) {
        String name = normalizeName(group.getName());
        ensureNameUnique(name, null);
        group.setName(name);
        if (group.getSortOrder() == null) {
            group.setSortOrder(nextSortOrder());
        }
        businessTypeGroupMapper.insert(group);
        syncGroupMembers(group.getId(), group.getBusinessTypeIds());
        return group;
    }

    @Override
    @Transactional
    public BusinessTypeGroup update(BusinessTypeGroup group) {
        BusinessTypeGroup existing = businessTypeGroupMapper.selectById(group.getId());
        if (existing == null || Integer.valueOf(1).equals(existing.getDeleted())) {
            throw new BusinessException(50001, "业务分组不存在");
        }
        String name = normalizeName(group.getName());
        ensureNameUnique(name, group.getId());
        existing.setName(name);
        existing.setSortOrder(group.getSortOrder() != null ? group.getSortOrder() : existing.getSortOrder());
        businessTypeGroupMapper.updateById(existing);
        syncGroupMembers(existing.getId(), group.getBusinessTypeIds());
        return existing;
    }

    @Override
    public void delete(Long id) {
        BusinessTypeGroup existing = businessTypeGroupMapper.selectById(id);
        if (existing == null || Integer.valueOf(1).equals(existing.getDeleted())) {
            throw new BusinessException(50001, "业务分组不存在");
        }

        Long businessCount = businessTypeMapper.selectCount(
                new QueryWrapper<BusinessType>()
                        .eq("group_id", id)
                        .eq("deleted", 0)
        );
        if (businessCount != null && businessCount > 0) {
            throw new BusinessException(50001, "分组下存在业务类型，无法删除");
        }

        businessTypeGroupMapper.deleteById(id);
    }

    private void ensureNameUnique(String name, Long excludeId) {
        QueryWrapper<BusinessTypeGroup> wrapper = new QueryWrapper<BusinessTypeGroup>()
                .eq("name", name)
                .eq("deleted", 0);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        if (businessTypeGroupMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(50001, "分组名称已存在");
        }
    }

    private String normalizeName(String name) {
        String normalized = name != null ? name.trim() : "";
        if (normalized.isEmpty()) {
            throw new BusinessException(50001, "分组名称不能为空");
        }
        if (normalized.length() > 50) {
            throw new BusinessException(50001, "分组名称不能超过50个字符");
        }
        return normalized;
    }

    private int nextSortOrder() {
        BusinessTypeGroup last = businessTypeGroupMapper.selectOne(
                new QueryWrapper<BusinessTypeGroup>()
                        .eq("deleted", 0)
                        .orderByDesc("sort_order", "id")
                        .last("LIMIT 1")
        );
        return last == null || last.getSortOrder() == null ? 0 : last.getSortOrder() + 1;
    }

    private void syncGroupMembers(Long groupId, List<Long> businessTypeIds) {
        Set<Long> targetIdSet = normalizeBusinessTypeIds(businessTypeIds);

        List<BusinessType> currentMembers = businessTypeMapper.selectList(
                new QueryWrapper<BusinessType>()
                        .eq("group_id", groupId)
                        .eq("deleted", 0)
        );
        Set<Long> currentIdSet = currentMembers.stream()
                .map(BusinessType::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!targetIdSet.isEmpty()) {
            List<BusinessType> selectedBusinessTypes = businessTypeMapper.selectBatchIds(targetIdSet);
            if (selectedBusinessTypes.size() != targetIdSet.size()) {
                throw new BusinessException(50001, "部分业务类型不存在，无法纳入分组");
            }

            for (BusinessType businessType : selectedBusinessTypes) {
                if (Integer.valueOf(1).equals(businessType.getDeleted())) {
                    throw new BusinessException(50001, "包含已删除的业务类型，无法纳入分组");
                }
                Long currentGroupId = businessType.getGroupId();
                if (currentGroupId != null && !currentGroupId.equals(groupId)) {
                    throw new BusinessException(50001, "只能选择未分组业务类型或当前分组内业务");
                }
            }
        }

        Set<Long> removedIds = new LinkedHashSet<>(currentIdSet);
        removedIds.removeAll(targetIdSet);
        if (!removedIds.isEmpty()) {
            businessTypeMapper.update(
                    null,
                    new UpdateWrapper<BusinessType>()
                            .in("id", removedIds)
                            .set("group_id", null)
            );
        }

        if (!targetIdSet.isEmpty()) {
            businessTypeMapper.update(
                    null,
                    new UpdateWrapper<BusinessType>()
                            .in("id", targetIdSet)
                            .set("group_id", groupId)
            );
        }
    }

    private Set<Long> normalizeBusinessTypeIds(List<Long> businessTypeIds) {
        if (businessTypeIds == null || businessTypeIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return businessTypeIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
