package com.futsal.tournament.domain;

import com.futsal.team.domain.Team;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 경기 매치 (대진표의 개별 경기)
 */
@Entity
@Table(name = "tournament_matches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TournamentMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    /**
     * 라운드 번호 (1라운드, 2라운드...)
     * - 토너먼트: 1(16강), 2(8강), 3(4강), 4(결승)
     * - 조별리그: 1-3(조별), 4+(결선)
     * - 스위스: 1, 2, 3...
     */
    @Column(nullable = false)
    private Integer round;

    /**
     * 매치 번호 (같은 라운드 내에서의 순서)
     */
    @Column(nullable = false)
    private Integer matchNumber;

    /**
     * 그룹 ID (조별리그인 경우만 사용)
     * 예: "A", "B", "C", "D"
     */
    @Column(length = 10)
    private String groupId;

    /**
     * 홈 팀
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team1_id")
    private Team team1;

    /**
     * 어웨이 팀
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team2_id")
    private Team team2;

    /**
     * 팀1 득점
     */
    @Column
    private Integer team1Score;

    /**
     * 팀2 득점
     */
    @Column
    private Integer team2Score;

    /**
     * 팀1 승부차기 득점
     */
    @Column(name = "team1penalty_score")
    private Integer team1PenaltyScore;

    /**
     * 팀2 승부차기 득점
     */
    @Column(name = "team2penalty_score")
    private Integer team2PenaltyScore;

    /**
     * 승자 팀
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private Team winner;

    /**
     * 경기 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MatchStatus status = MatchStatus.SCHEDULED;

    /**
     * 경기 예정 시간
     */
    @Column
    private LocalDateTime scheduledAt;

    /**
     * 경기장 이름
     */
    @Column(length = 100)
    private String venueName;

    /**
     * 경기 시작 시간
     */
    @Column
    private LocalDateTime startedAt;

    /**
     * 경기 종료 시간
     */
    @Column
    private LocalDateTime finishedAt;

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
     * 경기 상태
     */
    public enum MatchStatus {
        SCHEDULED("예정"),
        IN_PROGRESS("진행중"),
        FINISHED("종료"),
        CANCELLED("취소");

        private final String description;

        MatchStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // ===== 비즈니스 로직 =====

    /**
     * 경기 시작
     */
    public void start() {
        if (status != MatchStatus.SCHEDULED) {
            throw new IllegalStateException("예정된 경기만 시작할 수 있습니다.");
        }
        this.status = MatchStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * 경기 결과 입력 (승부차기 포함)
     */
    public void recordResult(Integer team1Score, Integer team2Score,
                             Integer team1PenaltyScore, Integer team2PenaltyScore) {
        if (team1 == null || team2 == null) {
            throw new IllegalStateException("양 팀이 모두 배정되어야 결과를 입력할 수 있습니다.");
        }

        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.team1PenaltyScore = team1PenaltyScore;
        this.team2PenaltyScore = team2PenaltyScore;

        // 승자 결정
        determineWinner();

        this.status = MatchStatus.FINISHED;
        this.finishedAt = LocalDateTime.now();
    }

    /**
     * 경기 결과 입력 (승부차기 없이)
     */
    public void recordResult(Integer team1Score, Integer team2Score) {
        recordResult(team1Score, team2Score, null, null);
    }

    /**
     * 승자 결정 (정규 시간 동점 시 승부차기로 결정)
     */
    private void determineWinner() {
        if (team1Score > team2Score) {
            this.winner = team1;
        } else if (team2Score > team1Score) {
            this.winner = team2;
        } else {
            // 동점인 경우 승부차기로 결정
            if (team1PenaltyScore != null && team2PenaltyScore != null) {
                if (team1PenaltyScore > team2PenaltyScore) {
                    this.winner = team1;
                } else if (team2PenaltyScore > team1PenaltyScore) {
                    this.winner = team2;
                }
                // 승부차기도 동점이면 winner = null
            }
            // 승부차기 없으면 무승부 (winner = null)
        }
    }

    /**
     * 동점 여부
     */
    public boolean isDrawInRegularTime() {
        return team1Score != null && team2Score != null && team1Score.equals(team2Score);
    }

    /**
     * 승부차기 진행 여부
     */
    public boolean hasPenaltyShootout() {
        return team1PenaltyScore != null && team2PenaltyScore != null;
    }

    /**
     * 경기 취소
     */
    public void cancel() {
        this.status = MatchStatus.CANCELLED;
    }

    /**
     * 팀 배정
     */
    public void assignTeam1(Team team) {
        this.team1 = team;
    }

    public void assignTeam2(Team team) {
        this.team2 = team;
    }

    /**
     * 경기 일정 업데이트
     */
    public void updateSchedule(LocalDateTime scheduledAt, String venueName) {
        this.scheduledAt = scheduledAt;
        this.venueName = venueName;
    }

    /**
     * 경기가 완료되었는지
     */
    public boolean isFinished() {
        return status == MatchStatus.FINISHED;
    }

    /**
     * 무승부인지
     */
    public boolean isDraw() {
        return isFinished() && winner == null;
    }

    /**
     * 패자 팀 반환
     */
    public Team getLoser() {
        if (!isFinished() || winner == null || team1 == null || team2 == null) {
            return null;
        }
        return winner.getId().equals(team1.getId()) ? team2 : team1;
    }
}
