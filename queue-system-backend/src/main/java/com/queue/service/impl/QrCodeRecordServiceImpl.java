package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.queue.entity.QrCodeRecord;
import com.queue.mapper.QrCodeRecordMapper;
import com.queue.service.QrCodeRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QrCodeRecordServiceImpl implements QrCodeRecordService {
    private final QrCodeRecordMapper mapper;

    @Override
    @Transactional
    public QrCodeRecord saveOrUpdate(Long regionId, String regionCode, String regionName, String url) {
        QrCodeRecord existing = mapper.selectOne(new LambdaQueryWrapper<QrCodeRecord>()
                .eq(QrCodeRecord::getRegionId, regionId));

        if (existing != null) {
            existing.setUrl(url);
            existing.setRegionCode(regionCode);
            existing.setRegionName(regionName);
            mapper.updateById(existing);
            return existing;
        }

        QrCodeRecord record = new QrCodeRecord();
        record.setRegionId(regionId);
        record.setRegionCode(regionCode);
        record.setRegionName(regionName);
        record.setUrl(url);
        mapper.insert(record);
        return record;
    }

    @Override
    public List<QrCodeRecord> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<QrCodeRecord>()
                .orderByDesc(QrCodeRecord::getCreatedAt));
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
