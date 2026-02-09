package com.futsal.auth.service;

import com.futsal.auth.domain.KakaoOAuth2UserInfo;
import com.futsal.auth.domain.OAuth2UserInfo;
import com.futsal.user.domain.User;
import com.futsal.user.domain.UserRole;
import com.futsal.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;

/**
 * OAuth2 사용자 처리 서비스
 * SRP: OAuth2 사용자 로드 및 DB 저장만 담당
 * DIP: OAuth2UserInfo 인터페이스에 의존
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // OCP: 새로운 OAuth2 제공자 추가 시 확장 가능
        OAuth2UserInfo userInfo = getOAuth2UserInfo(registrationId, attributes);
        
        User user = saveOrUpdate(userInfo);

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                attributes,
                "id"
        );
    }

    /**
     * OCP: 제공자별 UserInfo 생성 (확장에 열려있음)
     */
    private OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map<String, Object> attributes) {
        if ("kakao".equals(registrationId)) {
            return new KakaoOAuth2UserInfo(attributes);
        }
        // 추후 Google, Naver 등 추가 가능
        throw new OAuth2AuthenticationException("지원하지 않는 OAuth2 제공자입니다: " + registrationId);
    }

    /**
     * 사용자 저장 또는 업데이트
     */
    private User saveOrUpdate(OAuth2UserInfo userInfo) {
        return userRepository.findByKakaoId(userInfo.getProviderId())
                .map(existingUser -> {
                    existingUser.updateProfile(
                            userInfo.getNickname(),
                            userInfo.getProfileImageUrl()
                    );
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .kakaoId(userInfo.getProviderId())
                            .nickname(userInfo.getNickname())
                            .profileImageUrl(userInfo.getProfileImageUrl())
                            .role(UserRole.USER)
                            .build();
                    return userRepository.save(newUser);
                });
    }
}
