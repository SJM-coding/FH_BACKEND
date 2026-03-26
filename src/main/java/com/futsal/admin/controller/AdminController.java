package com.futsal.admin.controller;

import com.futsal.user.domain.User;
import com.futsal.user.domain.UserRole;
import com.futsal.user.domain.VerificationStatus;
import com.futsal.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 관리자 전용 API
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;

    /**
     * 인증 대기 목록 조회
     */
    @GetMapping("/verifications")
    public ResponseEntity<?> getPendingVerifications(
            @AuthenticationPrincipal User admin
    ) {
        if (admin == null || admin.getRole() != UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자 권한이 필요합니다"));
        }

        List<User> pendingUsers = userRepository.findByVerificationStatus(VerificationStatus.PENDING);

        List<Map<String, Object>> result = pendingUsers.stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * 전체 사용자 목록 조회 (인증 상태 포함)
     */
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(
            @AuthenticationPrincipal User admin,
            @RequestParam(required = false) String verificationStatus
    ) {
        if (admin == null || admin.getRole() != UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자 권한이 필요합니다"));
        }

        List<User> users;
        if (verificationStatus != null) {
            VerificationStatus status = VerificationStatus.valueOf(verificationStatus.toUpperCase());
            users = userRepository.findByVerificationStatus(status);
        } else {
            users = userRepository.findAll();
        }

        List<Map<String, Object>> result = users.stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * 인증 승인
     */
    @PostMapping("/verifications/{userId}/approve")
    public ResponseEntity<?> approveVerification(
            @AuthenticationPrincipal User admin,
            @PathVariable Long userId
    ) {
        if (admin == null || admin.getRole() != UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자 권한이 필요합니다"));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        try {
            user.approveVerification();
            userRepository.save(user);
            return ResponseEntity.ok(toUserResponse(user));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 인증 거절
     */
    @PostMapping("/verifications/{userId}/reject")
    public ResponseEntity<?> rejectVerification(
            @AuthenticationPrincipal User admin,
            @PathVariable Long userId
    ) {
        if (admin == null || admin.getRole() != UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자 권한이 필요합니다"));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        try {
            user.rejectVerification();
            userRepository.save(user);
            return ResponseEntity.ok(toUserResponse(user));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 대시보드 통계
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(
            @AuthenticationPrincipal User admin
    ) {
        if (admin == null || admin.getRole() != UserRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자 권한이 필요합니다"));
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("pendingVerifications", userRepository.countByVerificationStatus(VerificationStatus.PENDING));
        stats.put("verifiedUsers", userRepository.countByVerificationStatus(VerificationStatus.VERIFIED));

        return ResponseEntity.ok(stats);
    }

    private Map<String, Object> toUserResponse(User user) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("nickname", user.getNickname());
        response.put("profileImageUrl", user.getProfileImageUrl());
        response.put("role", user.getRole());
        response.put("verificationStatus", user.getVerificationStatus());
        response.put("verifiedAt", user.getVerifiedAt());
        response.put("createdAt", user.getCreatedAt());
        return response;
    }
}
