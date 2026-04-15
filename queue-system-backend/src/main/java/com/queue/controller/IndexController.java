package com.queue.controller;

import com.queue.common.Result;
import com.queue.config.ServerConfig;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class IndexController {

    private final ServerConfig serverConfig;

    @GetMapping("/")
    public void index(HttpServletResponse response) throws Exception {
        response.sendRedirect(serverConfig.getFrontendBaseUrl() + "/");
    }

    @GetMapping("/admin")
    public void admin(HttpServletResponse response) throws Exception {
        response.sendRedirect(serverConfig.getFrontendBaseUrl() + "/admin");
    }

    @GetMapping("/login")
    public void login(HttpServletResponse response) throws Exception {
        response.sendRedirect(serverConfig.getFrontendBaseUrl() + "/login");
    }

    @GetMapping("/counter")
    public void counter(HttpServletResponse response) throws Exception {
        response.sendRedirect(serverConfig.getFrontendBaseUrl() + "/counter");
    }

    @GetMapping("/display")
    public void display(HttpServletResponse response) throws Exception {
        response.sendRedirect(serverConfig.getFrontendBaseUrl() + "/display");
    }

    @GetMapping("/appointment")
    public void appointment(HttpServletResponse response) throws Exception {
        response.sendRedirect(serverConfig.getFrontendBaseUrl() + "/appointment");
    }

    @GetMapping("/api/v1/health")
    @ResponseBody
    public Result<Map<String, Object>> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("timestamp", LocalDateTime.now());
        return Result.ok(result);
    }
}
