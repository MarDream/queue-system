package com.queue.service;

import com.queue.dto.BusinessTypeDetailVO;
import com.queue.entity.BusinessType;

import java.util.List;
import java.util.Set;

public interface BusinessTypeService {
    List<BusinessType> listAll();
    BusinessType getById(Long id);
    BusinessType create(BusinessType businessType);
    BusinessType update(BusinessType businessType);
    void delete(Long id);
    List<BusinessTypeDetailVO> getBusinessTypeDetail(Long businessTypeId);
    List<BusinessTypeDetailVO> getBusinessTypeDetail(Long businessTypeId, Set<Long> allowedRegionIds);
}
