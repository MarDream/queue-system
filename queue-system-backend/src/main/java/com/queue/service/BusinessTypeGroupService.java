package com.queue.service;

import com.queue.entity.BusinessTypeGroup;

import java.util.List;

public interface BusinessTypeGroupService {
    List<BusinessTypeGroup> listAll();
    BusinessTypeGroup create(BusinessTypeGroup group);
    BusinessTypeGroup update(BusinessTypeGroup group);
    void delete(Long id);
}
