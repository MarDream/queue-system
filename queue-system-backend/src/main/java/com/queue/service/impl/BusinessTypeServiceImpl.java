package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.queue.common.BusinessException;
import com.queue.common.ResultCode;
import com.queue.dto.BusinessTypeDetailVO;
import com.queue.entity.BusinessType;
import com.queue.entity.Counter;
import com.queue.entity.CounterBusiness;
import com.queue.entity.Region;
import com.queue.entity.Ticket;
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
    private final TicketMapper ticketMapper;
    private final CounterBusinessMapper counterBusinessMapper;
    private final CounterMapper counterMapper;
    private final RegionMapper regionMapper;

    public BusinessTypeServiceImpl(BusinessTypeMapper businessTypeMapper,
                                   TicketMapper ticketMapper,
                                   CounterBusinessMapper counterBusinessMapper,
                                   CounterMapper counterMapper,
                                   RegionMapper regionMapper) {
        this.businessTypeMapper = businessTypeMapper;
        this.ticketMapper = ticketMapper;
        this.counterBusinessMapper = counterBusinessMapper;
        this.counterMapper = counterMapper;
        this.regionMapper = regionMapper;
    }

    @Override
    public List<BusinessType> listAll() {
        return businessTypeMapper.selectList(new QueryWrapper<BusinessType>().orderByAsc("sort_order"));
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
        // Auto-generate prefix from name if not provided
        String name = businessType.getName() != null ? businessType.getName().trim() : "";
        if (name.isEmpty()) {
            throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "业务名称不能为空");
        }
        businessType.setName(name);

        // If prefix is empty or same as name (user didn't specify), auto-generate
        String prefix = businessType.getPrefix() != null ? businessType.getPrefix().trim() : "";
        if (prefix.isEmpty()) {
            prefix = generateAvailablePrefix(name);
        }
        businessType.setPrefix(prefix);

        // Check prefix uniqueness (global)
        BusinessType existingPrefix = businessTypeMapper.selectOne(
            new QueryWrapper<BusinessType>().eq("prefix", prefix)
        );
        if (existingPrefix != null) {
            throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "前缀已存在");
        }

        // Check name uniqueness (global)
        BusinessType nameDup = businessTypeMapper.selectOne(
            new QueryWrapper<BusinessType>().eq("name", name)
        );
        if (nameDup != null) {
            throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "业务名称已存在");
        }

        businessTypeMapper.insert(businessType);
        return businessType;
    }

    /**
     * Generate an available prefix from the business name.
     * Uses the first character's pinyin initial (uppercase).
     * If taken, appends A-Z until available.
     */
    private String generateAvailablePrefix(String name) {
        String initials = PinyinUtil.getPinyinInitials(name);
        char base = initials.isEmpty() ? 'X' : initials.charAt(0);

        // Try single letter first
        BusinessType existing = businessTypeMapper.selectOne(
            new QueryWrapper<BusinessType>().eq("prefix", String.valueOf(base))
        );
        if (existing == null) {
            return String.valueOf(base);
        }

        // Appended letter if taken
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
        // Normalize name and prefix
        String normalizedPrefix = businessType.getPrefix().trim();
        String normalizedName = businessType.getName() != null ? businessType.getName().trim() : "";
        // Check prefix global uniqueness, excluding self
        BusinessType dup = businessTypeMapper.selectOne(
            new QueryWrapper<BusinessType>()
                .eq("prefix", normalizedPrefix)
                .ne("id", businessType.getId())
        );
        if (dup != null) throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "前缀已存在");
        // Check name global uniqueness, excluding self (case-insensitive, whitespace-trimmed)
        BusinessType nameDup = businessTypeMapper.selectOne(
            new QueryWrapper<BusinessType>()
                .eq("name", normalizedName)
                .ne("id", businessType.getId())
        );
        if (nameDup != null) throw new BusinessException(ResultCode.INVALID_BUSINESS_TYPE.getCode(), "业务名称已存在");
        businessType.setPrefix(normalizedPrefix);
        businessType.setName(normalizedName);
        businessTypeMapper.updateById(businessType);
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
        // Delete business type
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
}
