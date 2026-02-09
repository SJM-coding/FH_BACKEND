package com.futsal.user.service;

import com.futsal.user.dto.UserUpdateRequest;
import com.futsal.user.domain.User;
import com.futsal.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * 사용자 프로필 업데이트
     */
    @Transactional
    public Map<String, Object> updateProfile(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        // 닉네임과 프로필 이미지 업데이트
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.updateNickname(request.getNickname());
        }

        if (request.getProfileImageUrl() != null) {
            user.updateProfileImage(request.getProfileImageUrl());
        }

        // 변경사항 저장
        User updated = userRepository.save(user);

        // 응답 생성
        Map<String, Object> response = new HashMap<>();
        response.put("id", updated.getId());
        response.put("kakaoId", updated.getKakaoId());
        response.put("nickname", updated.getNickname());
        response.put("profileImageUrl", updated.getProfileImageUrl());
        response.put("role", updated.getRole());

        return response;
    }

    /**
     * 사용자 계정 삭제
     */
    @Transactional
    public void deleteAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        // TODO: 사용자가 등록한 대회들도 함께 삭제할지 결정 필요
        // 현재는 사용자만 삭제 (대회는 유지)
        userRepository.delete(user);
    }
}
