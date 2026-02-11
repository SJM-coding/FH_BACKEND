package com.futsal.config;

import com.futsal.auth.filter.JwtAuthenticationFilter;
import com.futsal.auth.handler.OAuth2SuccessHandler;
import com.futsal.auth.service.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        boolean isLocal = Arrays.asList(environment.getActiveProfiles()).contains("local");

        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> {
                // 공개 API
                auth.requestMatchers(HttpMethod.GET, "/api/tournaments/**").permitAll();
                auth.requestMatchers("/api/auth/refresh").permitAll();
                auth.requestMatchers("/api/auth/exchange").permitAll();
                auth.requestMatchers("/api/auth/logout").permitAll();
                auth.requestMatchers("/login/**", "/oauth2/**").permitAll();
                if (isLocal) {
                    auth.requestMatchers("/h2-console/**").permitAll();
                }
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
