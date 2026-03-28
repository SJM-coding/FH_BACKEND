package com.futsal.user.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 엔티티
 * SRP: 사용자 정보만 담당
 */
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "kakao_id")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, name = "kakao_id")
    private Long kakaoId;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    @Builder.Default
    private Boolean roleSelected = false;

    /**
     * 사용자가 직접 업로드한 프로필 이미지 여부
     * true면 카카오 로그인 시 덮어쓰지 않음
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean customProfileImage = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 개최자 인증 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.NONE;

    /**
     * 인증 완료 일시
     */
    private LocalDateTime verifiedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 닉네임 업데이트
     */
    public void updateNickname(String nickname) {
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
    }

    /**
     * 프로필 이미지 업데이트 (카카오 등 외부)
     */
    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * 커스텀 프로필 이미지 업데이트 (S3 업로드)
     */
    public void updateCustomProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
        this.customProfileImage = true;
    }

    /**
     * 커스텀 프로필 이미지 여부
     */
    public Boolean hasCustomProfileImage() {
        return Boolean.TRUE.equals(customProfileImage);
    }

    /**
     * 프로필 정보 업데이트 (통합)
     */
    public void updateProfile(String nickname, String profileImageUrl) {
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    /**
     * 역할 선택 (첫 로그인 시)
     */
    public void selectRole(UserRole role) {
        if (role == UserRole.ADMIN) {
            throw new IllegalArgumentException("ADMIN role cannot be selected");
        }
        this.role = role;
        this.roleSelected = true;
    }

    /**
     * 역할 선택 여부 반환
     */
    public Boolean getRoleSelected() {
        return roleSelected;
    }

    /**
     * 인증 신청
     */
    public void requestVerification() {
        if (this.role != UserRole.ORGANIZER) {
            throw new IllegalStateException("개최자만 인증 신청이 가능합니다");
        }
        if (this.verificationStatus == VerificationStatus.VERIFIED) {
            throw new IllegalStateException("이미 인증된 사용자입니다");
        }
        if (this.verificationStatus == VerificationStatus.PENDING) {
            throw new IllegalStateException("이미 인증 심사 중입니다");
        }
        this.verificationStatus = VerificationStatus.PENDING;
    }

    /**
     * 인증 승인 (관리자)
     */
    public void approveVerification() {
        if (this.verificationStatus != VerificationStatus.PENDING) {
            throw new IllegalStateException("심사 중인 요청만 승인할 수 있습니다");
        }
        this.verificationStatus = VerificationStatus.VERIFIED;
        this.verifiedAt = LocalDateTime.now();
    }

    /**
     * 인증 거절 (관리자)
     */
    public void rejectVerification() {
        if (this.verificationStatus != VerificationStatus.PENDING) {
            throw new IllegalStateException("심사 중인 요청만 거절할 수 있습니다");
        }
        this.verificationStatus = VerificationStatus.REJECTED;
    }

    /**
     * 인증 여부 확인
     */
    public boolean isVerified() {
        return this.verificationStatus == VerificationStatus.VERIFIED;
    }
}
