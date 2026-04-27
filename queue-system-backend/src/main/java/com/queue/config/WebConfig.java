package com.queue.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ServerConfig serverConfig;

    @Bean
    public CharacterEncodingFilter characterEncodingFilter() {
        CharacterEncodingFilter filter = new CharacterEncodingFilter();
        filter.setEncoding("UTF-8");
        filter.setForceRequestEncoding(true);
        filter.setForceResponseEncoding(true);
        return filter;
    }

    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setDefaultCharset(StandardCharsets.UTF_8);
        return converter;
    }

    @Bean
    public StringHttpMessageConverter stringHttpMessageConverter() {
        return new StringHttpMessageConverter(StandardCharsets.UTF_8);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String host = ServerConfig.getLocalIp();
        Set<String> patterns = new LinkedHashSet<>(Arrays.asList(
            "http://localhost:*",
            "https://localhost:*",
            "http://127.0.0.1:*",
            "https://127.0.0.1:*"
        ));

        addHostPatterns(patterns, host);
        addFrontendBaseUrlPattern(patterns, serverConfig.getFrontendBaseUrl());

        // 从配置读取额外的 CORS 允许源
        String extra = serverConfig.getExtraCorsOrigins();
        if (extra != null && !extra.isBlank()) {
            for (String origin : extra.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    patterns.add(trimmed);
                }
            }
        }
        registry.addMapping("/**")
                .allowedOriginPatterns(patterns.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    private void addHostPatterns(Set<String> patterns, String host) {
        if (host == null || host.isBlank()) {
            return;
        }
        String trimmed = host.trim();
        patterns.add("http://" + trimmed);
        patterns.add("https://" + trimmed);
        patterns.add("http://" + trimmed + ":*");
        patterns.add("https://" + trimmed + ":*");
    }

    private void addFrontendBaseUrlPattern(Set<String> patterns, String frontendBaseUrl) {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(frontendBaseUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            if (scheme == null || host == null) {
                return;
            }
            String origin = port > 0 ? scheme + "://" + host + ":" + port : scheme + "://" + host;
            patterns.add(origin);
        } catch (IllegalArgumentException ignored) {
            // Ignore invalid custom frontend URL; extra origins can still be used as fallback.
        }
    }
}
