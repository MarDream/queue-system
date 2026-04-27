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
    public QrCodeRecord saveOrUpdate(Long regionId, String regionCode, String regionName, String url, String createdBy) {
        // 查询时不考虑 deleted 字段，直接按 regionId 查找
        QrCodeRecord existing = mapper.selectOne(new LambdaQueryWrapper<QrCodeRecord>()
                .eq(QrCodeRecord::getRegionId, regionId));

        if (existing != null) {
            // 只更新需要的字段，不更新 region_id（避免唯一键冲突）
            existing.setUrl(url);
            existing.setRegionName(regionName);
            if (createdBy != null && !createdBy.isEmpty()) {
                existing.setCreatedBy(createdBy);
            }
            // 明确设置 deleted=0，防止被更新为其他值
            existing.setDeleted(0);
            mapper.updateById(existing);
            return existing;
        }

        QrCodeRecord record = new QrCodeRecord();
        record.setRegionId(regionId);
        record.setRegionCode(regionCode);
        record.setRegionName(regionName);
        record.setUrl(url);
        record.setCreatedBy(createdBy);
        record.setDeleted(0);
        mapper.insert(record);
        return record;
    }

    @Override
    public List<QrCodeRecord> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<QrCodeRecord>()
                .eq(QrCodeRecord::getDeleted, 0)
                .orderByDesc(QrCodeRecord::getCreatedAt));
    }

    @Override
    public void delete(Long id) {
        mapper.physicalDeleteById(id);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        mapper.physicalDeleteByIds(ids);
    }

    @Override
    public void deleteByRegionIds(List<Long> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) return;
        mapper.physicalDeleteByRegionIds(regionIds);
    }
}
