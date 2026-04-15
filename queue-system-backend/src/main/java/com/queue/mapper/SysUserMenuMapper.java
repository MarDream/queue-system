package com.queue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.queue.entity.SysUserMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysUserMenuMapper extends BaseMapper<SysUserMenu> {

    @Select("SELECT menu_id FROM sys_user_menu WHERE user_id = #{userId}")
    List<Long> selectMenuIdsByUserId(Long userId);
}
