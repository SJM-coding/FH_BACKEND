package com.futsal.team.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 팀 수상 경력
 */
@Entity
@Table(name = "team_awards")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TeamAward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "team_name", nullable = false, length = 100)
    private String teamName;

    /**
     * 대회 ID (선택 - 대회와 연결되지 않은 수상도 가능)
     */
    @Column(name = "tournament_id")
    private Long tournamentId;

    /**
     * 대회명 (대회가 삭제되어도 기록 유지)
     */
    @Column(name = "tournament_name", length = 100)
    private String tournamentName;

    /**
     * 수상 종류
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AwardType awardType;

    /**
     * 수상 날짜
     */
    @Column(nullable = false)
    private LocalDate awardDate;

    /**
     * 부가 설명 (예: "득점왕 - 홍길동 10골")
     */
    @Column(length = 200)
    private String description;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

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
     * 수상 정보 수정
     */
    public void update(Long tournamentId, String tournamentName, AwardType awardType,
                       LocalDate awardDate, String description) {
        this.tournamentId = tournamentId;
        this.tournamentName = tournamentName;
        this.awardType = awardType;
        this.awardDate = awardDate;
        this.description = description;
    }
}
