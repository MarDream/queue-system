package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.queue.common.BusinessException;
import com.queue.common.ResultCode;
import com.queue.dto.RegionPageRequest;
import com.queue.dto.RegionSortRequest;
import com.queue.entity.Region;
import com.queue.mapper.RegionMapper;
import com.queue.service.RegionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegionServiceImpl implements RegionService {
    private final RegionMapper regionMapper;

    @Override
    public List<Region> listByLevel(String level) {
        return regionMapper.selectList(new LambdaQueryWrapper<Region>()
                .eq(Region::getLevel, level)
                .orderByAsc(Region::getSortOrder));
    }

    @Override
    public List<Region> listByParentId(Long parentId) {
        return regionMapper.selectList(new LambdaQueryWrapper<Region>()
                .eq(Region::getParentId, parentId)
                .orderByAsc(Region::getSortOrder));
    }

    @Override
    public Region getById(Long id) {
        return regionMapper.selectById(id);
    }

    @Override
    public Region getByCode(String code) {
        return regionMapper.selectOne(new LambdaQueryWrapper<Region>()
                .eq(Region::getRegionCode, code));
    }

    @Override
    public String getFullRegionName(String code) {
        Region region = getByCode(code);
        if (region == null) return "";
        if (region.getParentId() != null) {
            Region parent = getById(region.getParentId());
            if (parent != null) {
                return parent.getRegionName() + "-" + region.getRegionName();
            }
        }
        return region.getRegionName();
    }

    @Override
    public Region create(Region region) {
        checkRegionCodeDuplicate(region.getRegionCode(), null);
        regionMapper.insert(region);
        return region;
    }

    @Override
    public Region update(Region region) {
        Region existing = regionMapper.selectById(region.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
        checkRegionCodeDuplicate(region.getRegionCode(), region.getId());
        regionMapper.updateById(region);
        return region;
    }

    /**
     * 校验区划代码唯一性（排除指定id）
     */
    private void checkRegionCodeDuplicate(String regionCode, Long excludeId) {
        if (regionCode == null || regionCode.trim().isEmpty()) {
            return;
        }
        Region duplicate = getByCode(regionCode.trim());
        if (duplicate != null && !duplicate.getId().equals(excludeId)) {
            String fullName = buildFullPath(duplicate);
            throw new BusinessException(40001, fullName + "|" + duplicate.getRegionCode());
        }
    }

    /**
     * 构建区域的完整路径名称（如：深圳市-宝安区-新安街道）
     */
    private String buildFullPath(Region region) {
        StringBuilder sb = new StringBuilder(region.getRegionName());
        Region current = region;
        while (current.getParentId() != null) {
            Region parent = getById(current.getParentId());
            if (parent == null) break;
            sb.insert(0, parent.getRegionName() + "-");
            current = parent;
        }
        return sb.toString();
    }

    @Override
    public void delete(Long id) {
        // 检查是否有子区域
        List<Region> children = listByParentId(id);
        if (!children.isEmpty()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
        regionMapper.deleteById(id);
    }

    @Override
    public List<Region> listAll() {
        return regionMapper.selectList(new LambdaQueryWrapper<Region>()
                .orderByAsc(Region::getLevel)
                .orderByAsc(Region::getSortOrder));
    }

    @Override
    @Transactional
    public void batchUpdateSort(List<RegionSortRequest> requests) {
        if (requests == null || requests.isEmpty()) return;

        // 校验：所有区域的 parentId 必须一致（同层级排序）
        Long expectedParentId = null;
        for (RegionSortRequest req : requests) {
            Region region = regionMapper.selectById(req.getId());
            if (region == null) {
                throw new BusinessException(50001, "区域不存在: id=" + req.getId());
            }
            if (expectedParentId == null) {
                expectedParentId = region.getParentId();
            } else if (!expectedParentId.equals(region.getParentId())) {
                throw new BusinessException(50001, "不允许跨层级排序，区域「" + region.getRegionName() + "」与同组其他区域不在同一层级");
            }
        }

        for (RegionSortRequest req : requests) {
            Region region = new Region();
            region.setId(req.getId());
            region.setSortOrder(req.getSortOrder());
            regionMapper.updateById(region);
        }
    }

    @Override
    public IPage<Region> listPage(RegionPageRequest request) {
        int pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
        Page<Region> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<Region> wrapper = new LambdaQueryWrapper<>();

        // 按城市筛选：包含市级区域本身 + 该市下所有子/孙级区域
        if (request.getCityId() != null) {
            // 先查该市下所有镇区ID
            List<Region> towns = regionMapper.selectList(
                    new LambdaQueryWrapper<Region>().eq(Region::getParentId, request.getCityId()));
            List<Long> townIds = towns.stream().map(Region::getId).collect(Collectors.toList());

            // 查询条件：id = cityId OR parentId = cityId OR parentId IN (townIds)
            wrapper.and(w -> {
                w.eq(Region::getId, request.getCityId());
                w.or().eq(Region::getParentId, request.getCityId());
                if (!townIds.isEmpty()) {
                    w.or().in(Region::getParentId, townIds);
                }
            });
        }

        // 按级别筛选
        if (StringUtils.hasText(request.getLevel())) {
            wrapper.eq(Region::getLevel, request.getLevel());
        }

        // 关键词搜索
        if (StringUtils.hasText(request.getKeyword())) {
            String kw = "%" + request.getKeyword().trim() + "%";
            wrapper.and(w -> w.like(Region::getRegionName, kw).or().like(Region::getRegionCode, kw));
        }

        wrapper.orderByAsc(Region::getLevel).orderByAsc(Region::getSortOrder);

        IPage<Region> result = regionMapper.selectPage(page, wrapper);

        // 补充 parentName
        if (!result.getRecords().isEmpty()) {
            List<Long> parentIds = result.getRecords().stream()
                    .map(Region::getParentId)
                    .filter(id -> id != null)
                    .distinct()
                    .collect(Collectors.toList());

            Map<Long, String> parentNameMap = Collections.emptyMap();
            if (!parentIds.isEmpty()) {
                List<Region> parents = regionMapper.selectBatchIds(parentIds);
                parentNameMap = parents.stream()
                        .collect(Collectors.toMap(Region::getId, Region::getRegionName));
            }

            final Map<Long, String> nameMap = parentNameMap;
            for (Region region : result.getRecords()) {
                if (region.getParentId() != null) {
                    String parentName = nameMap.get(region.getParentId());
                    if (parentName != null) {
                        region.setParentName(parentName);
                    }
                }
            }
        }

        return result;
    }

    @Override
    public List<Long> getDescendantRegionIds(Long regionId) {
        if (regionId == null) {
            return Collections.emptyList();
        }
        return regionMapper.selectDescendantIds(regionId);
    }
}
