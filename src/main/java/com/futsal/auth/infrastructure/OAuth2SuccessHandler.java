package com.futsal.auth.infrastructure;

import com.futsal.auth.domain.KakaoOAuth2UserInfo;
import com.futsal.auth.infrastructure.JwtTokenProvider;
import com.futsal.auth.application.RefreshTokenService;
import com.futsal.auth.application.OAuth2CodeService;
import com.futsal.user.domain.User;
import com.futsal.user.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * OAuth2 로그인 성공 핸들러
 * 방식 3: 임시 코드 발급 → 프론트가 별도 API로 토큰 교환
 */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final OAuth2CodeService oauth2CodeService;

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

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

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // 카카오 사용자 정보 추출
        KakaoOAuth2UserInfo userInfo = new KakaoOAuth2UserInfo(attributes);
        
        // DB에서 사용자 조회
        User user = userRepository.findByKakaoId(userInfo.getProviderId())
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        // JWT 토큰 생성
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenService.store(
                user.getId(),
                refreshToken,
                Duration.ofMillis(jwtTokenProvider.getRefreshTokenValidityMs())
        );

        // RefreshToken은 HttpOnly 쿠키로
        addCookie(response, refreshCookieName, refreshToken, jwtTokenProvider.getRefreshTokenValidityMs());

        // AccessToken은 1회용 임시 코드로 변환 (5분 유효)
        String code = oauth2CodeService.createCode(accessToken);
        
        // 임시 코드만 URL로 전달 (보안 강화)
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", code)
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
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
}
