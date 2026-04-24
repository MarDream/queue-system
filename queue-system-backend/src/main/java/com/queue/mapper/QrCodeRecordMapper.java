package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.queue.entity.QrCodeRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QrCodeRecordMapper extends BaseMapper<QrCodeRecord> {

    /**
     * 物理删除（不经过逻辑删除）
     */
    @Delete("DELETE FROM qrcode_record WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
