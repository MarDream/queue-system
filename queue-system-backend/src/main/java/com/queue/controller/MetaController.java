package com.queue.controller;

import com.queue.common.Result;
import com.queue.config.AppVersionProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/meta")
@RequiredArgsConstructor
public class MetaController {

    private final AppVersionProvider appVersionProvider;

    @GetMapping("/version")
    public Result<Map<String, String>> version() {
        return Result.ok(Map.of("version", appVersionProvider.getVersion()));
    }
}
