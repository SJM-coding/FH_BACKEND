package com.futsal.config;

import com.futsal.auth.filter.JwtAuthenticationFilter;
import com.futsal.auth.handler.OAuth2SuccessHandler;
import com.futsal.auth.service.CustomOAuth2UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;

/**
 * Spring Security 설정
 * SRP: 보안 설정만 담당
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final Environment environment;
    private final CorsConfigurationSource corsConfigurationSource;

    /**
     * API 요청에 대한 인증 실패 시 401 응답 반환 (OAuth 리다이렉트 방지)
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"" + authException.getMessage() + "\"}");
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        boolean isLocal = Arrays.asList(environment.getActiveProfiles()).contains("local");

        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .exceptionHandling(exception ->
                exception.authenticationEntryPoint(authenticationEntryPoint())
            )
            .authorizeHttpRequests(auth -> {
                // 헬스체크
                auth.requestMatchers("/actuator/**").permitAll();
                // 사이트맵
                auth.requestMatchers("/sitemap.xml").permitAll();
                // 공개 API
                auth.requestMatchers(HttpMethod.GET, "/api/tournaments/**").permitAll();
                auth.requestMatchers("/api/auth/refresh").permitAll();
                auth.requestMatchers("/api/auth/exchange").permitAll();
                auth.requestMatchers("/api/auth/logout").permitAll();
                auth.requestMatchers("/login/**", "/oauth2/**").permitAll();
                // ShareCode 기반 대진표 접근 - 인증 불필요
                auth.requestMatchers("/api/share/**").permitAll();
                // 영상 API - 인증 불필요
                auth.requestMatchers("/api/videos/**").permitAll();
                if (isLocal) {
                    auth.requestMatchers("/h2-console/**").permitAll();
                }
                // 대회 생성/수정/삭제 - ORGANIZER, ADMIN만 허용
                auth.requestMatchers(HttpMethod.POST, "/api/tournaments").hasAnyRole("ORGANIZER", "ADMIN");
                auth.requestMatchers(HttpMethod.PUT, "/api/tournaments/**").hasAnyRole("ORGANIZER", "ADMIN");
                auth.requestMatchers(HttpMethod.DELETE, "/api/tournaments/**").hasAnyRole("ORGANIZER", "ADMIN");
                // 인증 필요
                auth.anyRequest().authenticated();
            })
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo ->
                    userInfo.userService(customOAuth2UserService)
                )
                .successHandler(oAuth2SuccessHandler)
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // H2 Console 프레임 허용 (local only)
        if (isLocal) {
            http.headers(headers -> headers.frameOptions(frame -> frame.disable()));
        }

        return http.build();
    }
}
