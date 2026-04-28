package com.queue.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.queue.dto.LoginRequest;
import com.queue.dto.LoginVO;
import com.queue.entity.Region;
import com.queue.entity.SysUser;
import com.queue.mapper.RegionMapper;
import com.queue.mapper.SysButtonMapper;
import com.queue.mapper.SysMenuMapper;
import com.queue.mapper.SysPermissionMapper;
import com.queue.mapper.SysUserButtonMapper;
import com.queue.mapper.SysUserMapper;
import com.queue.mapper.SysUserMenuMapper;
import com.queue.util.JwtUtil;
import com.queue.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserServiceImplLoginTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysMenuMapper sysMenuMapper;
    @Mock
    private SysButtonMapper sysButtonMapper;
    @Mock
    private SysPermissionMapper sysPermissionMapper;
    @Mock
    private SysUserMenuMapper sysUserMenuMapper;
    @Mock
    private SysUserButtonMapper sysUserButtonMapper;
    @Mock
    private RegionMapper regionMapper;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private com.queue.config.ServerConfig serverConfig;
    @Mock
    private Environment environment;

    private SysUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SysUserServiceImpl(
                sysUserMapper,
                sysMenuMapper,
                sysButtonMapper,
                sysPermissionMapper,
                sysUserMenuMapper,
                sysUserButtonMapper,
                regionMapper,
                jwtUtil,
                stringRedisTemplate,
                mailSender,
                serverConfig,
                environment
        );
    }

    @Test
    void windowOperatorLoginShouldNotScanExpiredTickets() {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("operator");
        user.setName("窗口人员");
        user.setRole("WINDOW_OPERATOR");
        user.setRegionId(2L);
        user.setStatus(1);
        user.setPassword(PasswordUtil.encodeBCrypt("password123"));

        Region region = new Region();
        region.setId(2L);
        region.setRegionName("南山区");
        region.setRegionCode("440305");

        LoginRequest request = new LoginRequest();
        request.setUsername("operator");
        request.setPassword("password123");

        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(jwtUtil.generateToken(7L, "operator", "WINDOW_OPERATOR")).thenReturn("jwt-token");
        when(sysUserMenuMapper.selectMenuIdsByUserId(7L)).thenReturn(List.of());
        when(sysUserButtonMapper.selectButtonIdsByUserId(7L)).thenReturn(List.of());
        when(sysPermissionMapper.selectMenuIdsByRole("WINDOW_OPERATOR")).thenReturn(List.of());
        when(sysPermissionMapper.selectButtonIdsByRole("WINDOW_OPERATOR")).thenReturn(List.of());
        when(regionMapper.selectById(2L)).thenReturn(region);

        LoginVO response = service.login(request);

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals("440305", response.getRegionCode());
        assertEquals("南山区", response.getRegionName());
    }
}
