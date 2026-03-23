package com.futsal.auth.domain;

import java.util.Map;

/**
 * 카카오 OAuth2 사용자 정보 구현체
 * LSP: OAuth2UserInfo 인터페이스를 올바르게 구현
 */
public class KakaoOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;
    private final Map<String, Object> kakaoAccount;
    private final Map<String, Object> profile;

    public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
        this.kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        this.profile = kakaoAccount != null 
            ? (Map<String, Object>) kakaoAccount.get("profile") 
            : null;
    }

    @Override
    public Long getProviderId() {
        return ((Number) attributes.get("id")).longValue();
    }

    @Override
    public String getNickname() {
        if (profile != null && profile.containsKey("nickname")) {
            return (String) profile.get("nickname");
        }
        return "사용자" + getProviderId();
    }

    @Override
    public String getProfileImageUrl() {
        if (profile != null && profile.containsKey("profile_image_url")) {
            String url = (String) profile.get("profile_image_url");
            // HTTP → HTTPS 변환 (Mixed Content 방지)
            if (url != null && url.startsWith("http://")) {
                return url.replace("http://", "https://");
            }
            return url;
        }
        return null;
    }

    @Override
    public String getProvider() {
        return "kakao";
    }
}
