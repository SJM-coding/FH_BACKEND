package com.futsal.auth.controller;

import com.futsal.auth.dto.RoleSelectionRequest;
import com.futsal.auth.jwt.JwtTokenProvider;
import com.futsal.auth.service.RefreshTokenService;
import com.futsal.user.domain.User;
import com.futsal.user.domain.UserRole;
import com.futsal.user.dto.UserUpdateRequest;
import com.futsal.user.repository.UserRepository;
import com.futsal.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 인증 관련 API 컨트롤러
 * SRP: 인증 관련 엔드포인트만 담당
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final com.futsal.auth.service.OAuth2CodeService oauth2CodeService;
    private final UserService userService;
    private final com.futsal.common.storage.S3Service s3Service;

    @Value("${app.jwt.access-cookie-name:accessToken}")
    private String accessCookieName;

    @Value("${app.jwt.refresh-cookie-name:refreshToken}")
    private String refreshCookieName;

    @Value("${app.jwt.cookie-domain:}")
    private String cookieDomain;

    @Value("${app.jwt.cookie-path:/}")
    private String cookiePath;

    @Value("${app.jwt.cookie-secure:true}")
    private boolean cookieSecure;

    @Value("${app.jwt.cookie-same-site:Lax}")
    private String cookieSameSite;

    /**
     * 임시 코드로 AccessToken 교환 (1회용)
     */
    @PostMapping("/exchange")
    public ResponseEntity<Map<String, String>> exchangeCode(
            @RequestBody Map<String, String> request
    ) {
        String code = request.get("code");
        if (code == null) {
            return ResponseEntity.badRequest().build();
        }

        // 1회용 코드로 AccessToken 조회 (사용 후 즉시 삭제)
        String accessToken = oauth2CodeService.consumeCode(code);
        if (accessToken == null) {
            // 코드가 유효하지 않거나 이미 사용됨
            return ResponseEntity.status(401).build();
        }

        Map<String, String> response = new HashMap<>();
        response.put("accessToken", accessToken);
        return ResponseEntity.ok(response);
    }

    /**
     * 현재 로그인한 사용자 정보 조회
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("kakaoId", user.getKakaoId());
        response.put("nickname", user.getNickname());
        response.put("profileImageUrl", user.getProfileImageUrl());
        response.put("role", user.getRole());
        response.put("roleSelected", user.getRoleSelected());

        return ResponseEntity.ok(response);
    }

    /**
     * 역할 선택 (첫 로그인 시)
     */
    @PutMapping("/role")
    public ResponseEntity<Map<String, Object>> selectRole(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody RoleSelectionRequest request
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        // 이미 역할을 선택한 경우
        if (Boolean.TRUE.equals(user.getRoleSelected())) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Role already selected");
            return ResponseEntity.status(409).body(errorResponse);
        }

        // ADMIN 역할 선택 시도 차단
        if ("ADMIN".equals(request.getRole())) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Cannot select ADMIN role");
            return ResponseEntity.status(403).body(errorResponse);
        }

        // 역할 선택
        UserRole selectedRole = UserRole.valueOf(request.getRole());
        user.selectRole(selectedRole);
        userRepository.save(user);

        // 새 토큰 발급 (role claim 업데이트)
        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), selectedRole.name());

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", newAccessToken);
        response.put("role", selectedRole.name());
        response.put("roleSelected", true);

        return ResponseEntity.ok(response);
    }

    /**
     * Refresh Token으로 새 Access Token 발급
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(
            @RequestBody(required = false) Map<String, String> request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String refreshToken = resolveCookie(httpRequest, refreshCookieName);
        if (refreshToken == null && request != null) {
            refreshToken = request.get("refreshToken");
        }

        if (refreshToken == null || !jwtTokenProvider.validateToken(refreshToken)) {
            return ResponseEntity.status(401).build();
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        String storedUserId = refreshTokenService.getUserId(refreshToken);
        if (storedUserId == null || !storedUserId.equals(userId.toString())) {
            return ResponseEntity.status(401).build();
        }
        
        // DB에서 사용자 정보 조회하여 role 가져오기
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));
        
        String newAccessToken = jwtTokenProvider.createAccessToken(userId, user.getRole().name());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);

        refreshTokenService.delete(refreshToken);
        refreshTokenService.store(
                userId,
                newRefreshToken,
                Duration.ofMillis(jwtTokenProvider.getRefreshTokenValidityMs())
        );

        // 패턴 2: AccessToken은 응답 body로만 전달 (메모리에 저장)
        // RefreshToken만 HttpOnly 쿠키로 설정
        addCookie(httpResponse, refreshCookieName, newRefreshToken, jwtTokenProvider.getRefreshTokenValidityMs());

        Map<String, String> response = new HashMap<>();
        response.put("accessToken", newAccessToken);

        return ResponseEntity.ok(response);
    }

    /**
     * 로그아웃 (Refresh Token 폐기 + 쿠키 삭제)
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String refreshToken = resolveCookie(httpRequest, refreshCookieName);
        if (refreshToken != null) {
            refreshTokenService.delete(refreshToken);
        }

        expireCookie(httpResponse, accessCookieName);
        expireCookie(httpResponse, refreshCookieName);

        return ResponseEntity.noContent().build();
    }

    /**
     * 프로필 이미지 업로드
     */
    @PostMapping("/profile/image")
    public ResponseEntity<String> uploadProfileImage(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        String imageUrl = s3Service.uploadProfileImage(file);
        return ResponseEntity.ok(imageUrl);
    }

    /**
     * 프로필 업데이트
     */
    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @AuthenticationPrincipal User user,
            @RequestBody UserUpdateRequest request
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> response = userService.updateProfile(user.getId(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * 계정 삭제
     */
    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal User user,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        // 계정 삭제
        userService.deleteAccount(user.getId());

        // 로그아웃 처리
        String refreshToken = resolveCookie(httpRequest, refreshCookieName);
        if (refreshToken != null) {
            refreshTokenService.delete(refreshToken);
        }

        expireCookie(httpResponse, accessCookieName);
        expireCookie(httpResponse, refreshCookieName);

        return ResponseEntity.noContent().build();
    }

    private void addCookie(HttpServletResponse response, String name, String value, long maxAgeMs) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path(cookiePath)
                .maxAge(Duration.ofMillis(maxAgeMs))
                .sameSite(cookieSameSite);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder.domain(cookieDomain);
        }

        response.addHeader("Set-Cookie", builder.build().toString());
    }

    private String resolveCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void expireCookie(HttpServletResponse response, String name) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path(cookiePath)
                .maxAge(Duration.ZERO)
                .sameSite(cookieSameSite);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder.domain(cookieDomain);
        }

        response.addHeader("Set-Cookie", builder.build().toString());
    }
}
