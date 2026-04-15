package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.queue.entity.SysUserButton;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysUserButtonMapper extends BaseMapper<SysUserButton> {

    @Select("SELECT button_id FROM sys_user_button WHERE user_id = #{userId}")
    List<Long> selectButtonIdsByUserId(Long userId);
}
