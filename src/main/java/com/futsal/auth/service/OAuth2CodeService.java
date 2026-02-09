package com.futsal.auth.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * OAuth2 임시 코드 관리 서비스
 * 1회용 코드로 보안 강화
 */
@Service
public class OAuth2CodeService {

    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    public OAuth2CodeService(org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 임시 코드 생성 및 저장
     * @param accessToken 저장할 AccessToken
     * @return 1회용 임시 코드
     */
    public String createCode(String accessToken) {
        String code = generateRandomCode();
        String key = buildKey(code);
        
        // 5분 유효, 1회용
        redisTemplate.opsForValue().set(key, accessToken, Duration.ofMinutes(5));
        
        return code;
    }

    /**
     * 임시 코드로 AccessToken 조회 (1회만 가능)
     * @param code 임시 코드
     * @return AccessToken (없으면 null)
     */
    public String consumeCode(String code) {
        String key = buildKey(code);
        String accessToken = redisTemplate.opsForValue().get(key);
        
        if (accessToken != null) {
            // 1회용이므로 즉시 삭제
            redisTemplate.delete(key);
        }
        
        return accessToken;
    }

    private String generateRandomCode() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String buildKey(String code) {
        return "oauth2:code:" + code;
    }
}
