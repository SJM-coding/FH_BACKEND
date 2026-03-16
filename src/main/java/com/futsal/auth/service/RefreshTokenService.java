package com.futsal.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    public RefreshTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void store(Long userId, String refreshToken, Duration ttl) {
        String key = buildKey(refreshToken);
        redisTemplate.opsForValue().set(key, userId.toString(), ttl);
    }

    public boolean exists(String refreshToken) {
        String key = buildKey(refreshToken);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public String getUserId(String refreshToken) {
        String key = buildKey(refreshToken);
        return redisTemplate.opsForValue().get(key);
    }

    public void delete(String refreshToken) {
        String key = buildKey(refreshToken);
        redisTemplate.delete(key);
    }

    private String buildKey(String refreshToken) {
        return "refresh:" + refreshToken;
    }
}
