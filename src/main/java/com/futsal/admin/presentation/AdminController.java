package com.futsal.admin.presentation;

import com.futsal.admin.application.AdminService;
import com.futsal.user.domain.User;
import com.futsal.user.domain.UserRole;
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

  private final AdminService adminService;

  /**
   * 인증 대기 목록 조회
   */
  @GetMapping("/verifications")
  public ResponseEntity<?> getPendingVerifications(
      @AuthenticationPrincipal User admin
  ) {
    if (!isAdmin(admin)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자 권한이 필요합니다"));
    }

    List<Map<String, Object>> result = adminService.getPendingVerifications()
        .stream()
        .map(this::toUserResponse)
        .collect(Collectors.toList());

    return ResponseEntity.ok(result);
  }

  /**
   * 전체 사용자 목록 조회 (인증 상태 필터 포함)
   */
  @GetMapping("/users")
  public ResponseEntity<?> getAllUsers(
      @AuthenticationPrincipal User admin,
      @RequestParam(required = false) String verificationStatus
  ) {
    if (!isAdmin(admin)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자 권한이 필요합니다"));
    }

    List<Map<String, Object>> result = adminService.getUsers(verificationStatus)
        .stream()
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
    if (!isAdmin(admin)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자 권한이 필요합니다"));
    }

    try {
      User updated = adminService.approveVerification(userId);
      return ResponseEntity.ok(toUserResponse(updated));
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
    if (!isAdmin(admin)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자 권한이 필요합니다"));
    }

    try {
      User updated = adminService.rejectVerification(userId);
      return ResponseEntity.ok(toUserResponse(updated));
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
    if (!isAdmin(admin)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자 권한이 필요합니다"));
    }

    return ResponseEntity.ok(adminService.getStats());
  }

  private boolean isAdmin(User user) {
    return user != null && user.getRole() == UserRole.ADMIN;
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
