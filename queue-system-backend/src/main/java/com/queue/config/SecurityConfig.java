package com.queue.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ServerConfig serverConfig;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - allow all
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/health").permitAll()
                .requestMatchers("/api/v1/business-types/**").permitAll()
                // Regions: GET is public (for kiosk/display), write requires auth
                .requestMatchers(HttpMethod.GET, "/api/v1/regions", "/api/v1/regions/**").permitAll()
                .requestMatchers("/api/v1/regions", "/api/v1/regions/**").authenticated()
                .requestMatchers("/api/v1/ticket/**").permitAll()
                .requestMatchers("/api/v1/queue/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/qrcode/generate").permitAll()
                // Admin stats endpoint - read-only, no side effects
                .requestMatchers(HttpMethod.GET, "/api/v1/admin/counters/*/stats").permitAll()
                // Admin endpoints require auth
                .requestMatchers("/api/v1/admin/**").authenticated()
                // Frontend routes - allow all
                .requestMatchers("/admin", "/login", "/counter", "/display", "/appointment").permitAll()
                // All other API endpoints require authentication
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
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
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(new ArrayList<>(patterns));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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
