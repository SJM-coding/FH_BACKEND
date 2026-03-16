package com.futsal.auth.domain;

/**
 * OAuth2 사용자 정보 인터페이스
 * ISP: 작고 구체적인 인터페이스
 * DIP: 추상화에 의존
 */
public interface OAuth2UserInfo {
    Long getProviderId();
    String getNickname();
    String getProfileImageUrl();
    String getProvider();
}
