package com.queue.service;

import com.queue.entity.QrCodeRecord;
import java.util.List;

public interface QrCodeRecordService {
    QrCodeRecord saveOrUpdate(Long regionId, String regionCode, String regionName, String url, String createdBy);
    List<QrCodeRecord> listAll();
    void delete(Long id);
    void deleteByIds(List<Long> ids);
    void deleteByRegionIds(List<Long> regionIds);
}
