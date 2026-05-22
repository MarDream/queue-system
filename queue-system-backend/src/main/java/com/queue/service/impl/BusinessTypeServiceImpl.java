package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.queue.common.BusinessException;
import com.queue.common.ResultCode;
import com.queue.dto.BusinessTypeDetailVO;
import com.queue.entity.BusinessType;
import com.queue.entity.BusinessTypeGroup;
import com.queue.entity.Counter;
import com.queue.entity.CounterBusiness;
import com.queue.entity.Region;
import com.queue.entity.Ticket;
import com.queue.mapper.BusinessTypeGroupMapper;
import com.queue.mapper.BusinessTypeMapper;
import com.queue.mapper.CounterBusinessMapper;
import com.queue.mapper.CounterMapper;
import com.queue.mapper.RegionMapper;
import com.queue.mapper.TicketMapper;
import com.queue.service.BusinessTypeService;
import com.queue.util.PinyinUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BusinessTypeServiceImpl implements BusinessTypeService {

    private final BusinessTypeMapper businessTypeMapper;
    private final BusinessTypeGroupMapper businessTypeGroupMapper;
    private final TicketMapper ticketMapper;
    private final CounterBusinessMapper counterBusinessMapper;
    private final CounterMapper counterMapper;
    private final RegionMapper regionMapper;

    public BusinessTypeServiceImpl(BusinessTypeMapper businessTypeMapper,
                                   BusinessTypeGroupMapper businessTypeGroupMapper,
                                   TicketMapper ticketMapper,
                                   CounterBusinessMapper counterBusinessMapper,
                                   CounterMapper counterMapper,
                                   RegionMapper regionMapper) {
        this.businessTypeMapper = businessTypeMapper;
        this.businessTypeGroupMapper = businessTypeGroupMapper;
        this.ticketMapper = ticketMapper;
        this.counterBusinessMapper = counterBusinessMapper;
        this.counterMapper = counterMapper;
        this.regionMapper = regionMapper;
    }

    @Override
    public List<BusinessType> listAll() {
        List<BusinessType> list = businessTypeMapper.selectList(
                new QueryWrapper<BusinessType>()
                        .eq("deleted", 0)
                        .orderByAsc("sort_order", "id")
        );
        return enrichGroupInfo(list);
    }

    @Override
    public BusinessType getById(Long id) {
        BusinessType result = businessTypeMapper.selectById(id);
        if (result == null) {
            throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE);
        }
        return result;
    }

    @Override
    public BusinessType create(BusinessType businessType) {
        String name = businessType.getName() != null ? businessType.getName().trim() : "";
        if (name.isEmpty()) {
            throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "业务名称不能为空");
        }
        validateGroupId(businessType.getGroupId());
        businessType.setName(name);

        // If prefix is empty, auto-generate from name
        String prefix = businessType.getPrefix() != null ? businessType.getPrefix().trim().toUpperCase() : "";
        if (prefix.isEmpty()) {
            prefix = generateAvailablePrefix(name);
        } else {
            // Validate prefix format: max 5 uppercase letters only
            if (!prefix.matches("^[A-Z]{1,5}$")) {
                throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "前缀仅允许1-5位大写字母");
            }
            // Check prefix uniqueness
            BusinessType existingPrefix = businessTypeMapper.selectOne(
                new QueryWrapper<BusinessType>().eq("prefix", prefix)
            );
            if (existingPrefix != null) {
                throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "前缀已存在");
            }
        }
        businessType.setPrefix(prefix);

        // Check name uniqueness
        BusinessType nameDup = businessTypeMapper.selectOne(
            new QueryWrapper<BusinessType>().eq("name", name)
        );
        if (nameDup != null) {
            throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "业务名称已存在");
        }

        businessTypeMapper.insert(businessType);
        populateGroupName(businessType);
        return businessType;
    }

    /**
     * Generate an available prefix from the business name.
     * Uses the first character's pinyin initial (uppercase).
     * If taken, appends letters until available.
     */
    private String generateAvailablePrefix(String name) {
        String initials = PinyinUtil.getPinyinInitials(name);
        char base = initials.isEmpty() ? 'X' : initials.charAt(0);
        base = Character.toUpperCase(base);

        // Try single letter first
        BusinessType existing = businessTypeMapper.selectOne(
            new QueryWrapper<BusinessType>().eq("prefix", String.valueOf(base))
        );
        if (existing == null) {
            return String.valueOf(base);
        }

        // Append letters if taken
        for (char c = 'A'; c <= 'Z'; c++) {
            String candidate = "" + base + c;
            existing = businessTypeMapper.selectOne(
                new QueryWrapper<BusinessType>().eq("prefix", candidate)
            );
            if (existing == null) {
                return candidate;
            }
        }

        // Extremely unlikely to reach here
        return base + String.valueOf(System.currentTimeMillis() % 1000);
    }

    @Override
    public BusinessType update(BusinessType businessType) {
        BusinessType existing = businessTypeMapper.selectById(businessType.getId());
        if (existing == null) throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE);

        String normalizedPrefix = businessType.getPrefix() != null ? businessType.getPrefix().trim().toUpperCase() : "";
        String normalizedName = businessType.getName() != null ? businessType.getName().trim() : "";
        if (normalizedName.isEmpty()) {
            throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "业务名称不能为空");
        }
        validateGroupId(businessType.getGroupId());

        // Validate prefix format: max 5 uppercase letters only
        if (!normalizedPrefix.matches("^[A-Z]{1,5}$")) {
            throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "前缀仅允许1-5位大写字母");
        }

        // Check prefix uniqueness, excluding self
        BusinessType dup = businessTypeMapper.selectOne(
            new QueryWrapper<BusinessType>()
                .eq("prefix", normalizedPrefix)
                .ne("id", businessType.getId())
        );
        if (dup != null) throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "前缀已存在");

        // Check name uniqueness, excluding self
        BusinessType nameDup = businessTypeMapper.selectOne(
            new QueryWrapper<BusinessType>()
                .eq("name", normalizedName)
                .ne("id", businessType.getId())
        );
        if (nameDup != null) throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "业务名称已存在");

        businessType.setPrefix(normalizedPrefix);
        businessType.setName(normalizedName);
        businessTypeMapper.updateById(businessType);
        populateGroupName(businessType);
        return businessType;
    }

    @Override
    public void delete(Long id) {
        // Check for associated tickets
        List<Ticket> tickets = ticketMapper.selectList(
            new QueryWrapper<Ticket>().eq("business_type_id", id)
        );
        if (!tickets.isEmpty()) {
            throw new BusinessException(50001, "该业务类型下存在票号记录，无法删除");
        }
        // Delete counter associations first
        counterBusinessMapper.delete(
            new QueryWrapper<CounterBusiness>().eq("business_type_id", id)
        );
        // 逻辑删除（实体有 @TableLogic 注解，使用 MyBatis-Plus 逻辑删除保持一致）
        businessTypeMapper.deleteById(id);
    }

    @Override
    public List<BusinessTypeDetailVO> getBusinessTypeDetail(Long businessTypeId) {
        return getBusinessTypeDetail(businessTypeId, null);
    }

    public List<BusinessTypeDetailVO> getBusinessTypeDetail(Long businessTypeId, Set<Long> allowedRegionIds) {
        BusinessType bt = businessTypeMapper.selectById(businessTypeId);
        if (bt == null) throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE);

        List<CounterBusiness> cbList = counterBusinessMapper.selectList(
            new QueryWrapper<CounterBusiness>().eq("business_type_id", businessTypeId)
        );

        // Load all regions for name lookup
        List<Region> regions = regionMapper.selectList(null);
        Map<Long, String> regionNameMap = regions.stream()
            .collect(Collectors.toMap(Region::getId, Region::getRegionName));

        // 区域权限过滤：过滤掉不在允许区域内的柜台
        if (allowedRegionIds != null && allowedRegionIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<BusinessTypeDetailVO> result = new ArrayList<>();
        for (CounterBusiness cb : cbList) {
            Counter counter = counterMapper.selectById(cb.getCounterId());
            if (counter == null) continue;

            // 区域权限过滤：跳过不在允许区域内的柜台
            if (allowedRegionIds != null && !allowedRegionIds.contains(counter.getRegionId())) {
                continue;
            }

            QueryWrapper<Ticket> ticketQw = new QueryWrapper<Ticket>()
                .eq("business_type_id", businessTypeId)
                .eq("counter_id", counter.getId());
            if (allowedRegionIds != null) {
                ticketQw.in("region_id", allowedRegionIds);
            }
            int ticketCount = (int) ticketMapper.selectList(ticketQw).size();

            BusinessTypeDetailVO vo = new BusinessTypeDetailVO();
            vo.setRegionName(regionNameMap.getOrDefault(counter.getRegionId(), "—"));
            vo.setCounterNumber(counter.getNumber());
            vo.setCounterName(counter.getName());
            vo.setOperatorName(counter.getOperatorName() != null ? counter.getOperatorName() : "—");
            vo.setTicketCount((long) ticketCount);
            result.add(vo);
        }
        return result;
    }

    private void validateGroupId(Long groupId) {
        if (groupId == null) {
            return;
        }
        BusinessTypeGroup group = businessTypeGroupMapper.selectById(groupId);
        if (group == null || Integer.valueOf(1).equals(group.getDeleted())) {
            throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "业务分组不存在");
        }
    }

    private List<BusinessType> enrichGroupInfo(List<BusinessType> list) {
        if (list == null || list.isEmpty()) {
            return list;
        }

        Map<Long, BusinessTypeGroup> groupMap = businessTypeGroupMapper.selectList(
                new QueryWrapper<BusinessTypeGroup>()
                        .eq("deleted", 0)
                        .orderByAsc("sort_order", "id")
        ).stream().collect(Collectors.toMap(BusinessTypeGroup::getId, group -> group));

        for (BusinessType item : list) {
            BusinessTypeGroup group = item.getGroupId() != null ? groupMap.get(item.getGroupId()) : null;
            item.setGroupName(group != null ? group.getName() : null);
        }

        list.sort((left, right) -> {
            Integer leftGroupSort = resolveGroupSortOrder(groupMap, left.getGroupId());
            Integer rightGroupSort = resolveGroupSortOrder(groupMap, right.getGroupId());
            int compareGroup = Integer.compare(leftGroupSort, rightGroupSort);
            if (compareGroup != 0) return compareGroup;
            int compareSort = Integer.compare(left.getSortOrder() != null ? left.getSortOrder() : 0,
                    right.getSortOrder() != null ? right.getSortOrder() : 0);
            if (compareSort != 0) return compareSort;
            return Long.compare(left.getId() != null ? left.getId() : 0L, right.getId() != null ? right.getId() : 0L);
        });
        return list;
    }

    private Integer resolveGroupSortOrder(Map<Long, BusinessTypeGroup> groupMap, Long groupId) {
        BusinessTypeGroup group = groupId != null ? groupMap.get(groupId) : null;
        if (group == null || group.getSortOrder() == null) {
            return Integer.MAX_VALUE;
        }
        return group.getSortOrder();
    }

    private void populateGroupName(BusinessType businessType) {
        if (businessType == null) {
            return;
        }
        if (businessType.getGroupId() == null) {
            businessType.setGroupName(null);
            return;
        }
        BusinessTypeGroup group = businessTypeGroupMapper.selectById(businessType.getGroupId());
        businessType.setGroupName(group != null ? group.getName() : null);
    }
}
