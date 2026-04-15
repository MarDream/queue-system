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

import java.util.Arrays;

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
                .requestMatchers("/api/v1/qrcode/**").permitAll()
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
        String ip = ServerConfig.getLocalIp();
        java.util.List<String> patterns = new java.util.ArrayList<>(Arrays.asList(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://" + ip + ":*"
        ));
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
        configuration.setAllowedOriginPatterns(patterns);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
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
}
