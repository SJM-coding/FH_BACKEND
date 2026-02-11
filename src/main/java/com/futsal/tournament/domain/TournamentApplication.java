package com.futsal.tournament.domain;

import com.futsal.team.domain.Team;
import com.futsal.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 대회 신청서 (Aggregate)
 * 
 * 도메인 규칙:
 * - 팀 단위로만 신청 가능
 * - 신청 시 필수/선택 정보 제출
 * - 대회 주최자만 승인/거부 가능
 * - 승인 대기 상태에서만 승인/거부 가능
 */
@Entity
@Table(name = "tournament_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TournamentApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant; // 신청자 (팀장)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    // 신청 시 제출한 추가 정보 (JSON)
    @ElementCollection
    @CollectionTable(
        name = "application_form_data",
        joinColumns = @JoinColumn(name = "application_id")
    )
    @MapKeyColumn(name = "field_name")
    @Column(name = "field_value", length = 1000)
    @Builder.Default
    private Map<String, String> formData = new HashMap<>();

    @Column(length = 1000)
    private String message; // 신청 메시지

    @Column(length = 1000)
    private String rejectReason; // 거부 사유

    @Column(nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    private LocalDateTime processedAt; // 승인/거부 처리 시간

    @PrePersist
    protected void onCreate() {
        appliedAt = LocalDateTime.now();
        if (status == null) {
            status = ApplicationStatus.PENDING;
        }
    }

    // ===== 도메인 로직 =====

    /**
     * 신청 승인
     */
    public void approve() {
        validateCanApprove();
        this.status = ApplicationStatus.APPROVED;
        this.processedAt = LocalDateTime.now();
        this.rejectReason = null;
    }

    /**
     * 신청 거부
     */
    public void reject(String reason) {
        validateCanReject();
        this.status = ApplicationStatus.REJECTED;
        this.processedAt = LocalDateTime.now();
        this.rejectReason = reason;
    }

    /**
     * 신청 취소
     */
    public void cancel() {
        validateCanCancel();
        this.status = ApplicationStatus.CANCELLED;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 승인 가능 여부 검증
     */
    private void validateCanApprove() {
        if (!status.canApprove()) {
            throw new IllegalStateException(
                String.format("현재 상태(%s)에서는 승인할 수 없습니다.", status.getDescription())
            );
        }
    }

    /**
     * 거부 가능 여부 검증
     */
    private void validateCanReject() {
        if (!status.canReject()) {
            throw new IllegalStateException(
                String.format("현재 상태(%s)에서는 거부할 수 없습니다.", status.getDescription())
            );
        }
    }

    /**
     * 취소 가능 여부 검증
     */
    private void validateCanCancel() {
        if (!status.canCancel()) {
            throw new IllegalStateException(
                String.format("현재 상태(%s)에서는 취소할 수 없습니다.", status.getDescription())
            );
        }
    }

    /**
     * 신청자 확인
     */
    public boolean isApplicant(Long userId) {
        return this.applicant != null && this.applicant.getId().equals(userId);
    }

    /**
     * 팀 확인
     */
    public boolean isTeamApplication(Long teamId) {
        return this.team != null && this.team.getId().equals(teamId);
    }

    /**
     * 추가 정보 설정
     */
    public void updateFormData(Map<String, String> formData) {
        if (formData != null) {
            this.formData.clear();
            this.formData.putAll(formData);
        }
    }
}
