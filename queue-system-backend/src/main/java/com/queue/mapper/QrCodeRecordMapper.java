package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.queue.entity.QrCodeRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface QrCodeRecordMapper extends BaseMapper<QrCodeRecord> {

    /**
     * 物理删除（不经过逻辑删除）
     */
    @Delete("DELETE FROM qrcode_record WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    @Delete("<script>DELETE FROM qrcode_record WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int physicalDeleteByIds(@Param("ids") List<Long> ids);

    @Delete("<script>DELETE FROM qrcode_record WHERE region_id IN <foreach collection='regionIds' item='rid' open='(' separator=',' close=')'>#{rid}</foreach></script>")
    int physicalDeleteByRegionIds(@Param("regionIds") List<Long> regionIds);
}
