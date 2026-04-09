package com.futsal.tournament.domain;

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

    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

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

    @Column(name = "team1_id")
    private Long team1Id;

    @Column(name = "team1_name", length = 100)
    private String team1Name;

    @Column(name = "team1_logo_url", length = 500)
    private String team1LogoUrl;

    @Column(name = "team2_id")
    private Long team2Id;

    @Column(name = "team2_name", length = 100)
    private String team2Name;

    @Column(name = "team2_logo_url", length = 500)
    private String team2LogoUrl;

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

    @Column(name = "winner_id")
    private Long winnerId;

    @Column(name = "winner_name", length = 100)
    private String winnerName;

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
     * 경기 결과 입력
     * 불변식:
     * - 양 팀이 모두 배정되어야 함
     * - 결선(isKnockout=true) 동점 시 승부차기 필수
     * - 승부차기 점수는 동점 불가
     * - 조별리그(isKnockout=false)에는 승부차기 입력 불가
     */
    public void recordResult(
        Integer team1Score, Integer team2Score,
        Integer team1PenaltyScore, Integer team2PenaltyScore,
        boolean isKnockout
    ) {
        if (team1Id == null || team2Id == null) {
            throw new IllegalStateException("양 팀이 모두 배정되어야 결과를 입력할 수 있습니다.");
        }

        boolean isDraw = team1Score != null && team2Score != null
            && team1Score.equals(team2Score);

        if (isKnockout && isDraw) {
            if (team1PenaltyScore == null || team2PenaltyScore == null) {
                throw new IllegalStateException(
                    "결선 토너먼트 동점 경기는 승부차기 점수를 입력해야 합니다.");
            }
            if (team1PenaltyScore.equals(team2PenaltyScore)) {
                throw new IllegalStateException("승부차기 점수는 동점일 수 없습니다.");
            }
        }

        if (!isKnockout && (team1PenaltyScore != null || team2PenaltyScore != null)) {
            throw new IllegalStateException("조별리그 경기에는 승부차기 점수를 입력할 수 없습니다.");
        }

        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.team1PenaltyScore = team1PenaltyScore;
        this.team2PenaltyScore = team2PenaltyScore;
        determineWinner();
        this.status = MatchStatus.FINISHED;
        this.finishedAt = LocalDateTime.now();
    }

    /**
     * 경기 결과 입력 (승부차기 없이, 조별리그용)
     */
    public void recordResult(Integer team1Score, Integer team2Score) {
        recordResult(team1Score, team2Score, null, null, false);
    }

    /**
     * 승자 결정 (정규 시간 동점 시 승부차기로 결정)
     */
    private void determineWinner() {
        if (team1Score > team2Score) {
            this.winnerId = team1Id;
            this.winnerName = team1Name;
        } else if (team2Score > team1Score) {
            this.winnerId = team2Id;
            this.winnerName = team2Name;
        } else {
            if (team1PenaltyScore != null && team2PenaltyScore != null) {
                if (team1PenaltyScore > team2PenaltyScore) {
                    this.winnerId = team1Id;
                    this.winnerName = team1Name;
                } else if (team2PenaltyScore > team1PenaltyScore) {
                    this.winnerId = team2Id;
                    this.winnerName = team2Name;
                }
            }
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
    public void assignTeam1(Long id, String name, String logoUrl) {
        this.team1Id = id;
        this.team1Name = name;
        this.team1LogoUrl = logoUrl;
    }

    public void assignTeam2(Long id, String name, String logoUrl) {
        this.team2Id = id;
        this.team2Name = name;
        this.team2LogoUrl = logoUrl;
    }

    /**
     * 승자를 다음 경기에 배정
     */
    public void advanceWinnerAsTeam1(TournamentMatch next) {
        if (winnerId == null) return;
        String name = winnerId.equals(team1Id) ? team1Name : team2Name;
        String logo = winnerId.equals(team1Id) ? team1LogoUrl : team2LogoUrl;
        next.assignTeam1(winnerId, name, logo);
    }

    public void advanceWinnerAsTeam2(TournamentMatch next) {
        if (winnerId == null) return;
        String name = winnerId.equals(team1Id) ? team1Name : team2Name;
        String logo = winnerId.equals(team1Id) ? team1LogoUrl : team2LogoUrl;
        next.assignTeam2(winnerId, name, logo);
    }

    /**
     * 패자를 다음 경기에 배정 (3·4위전용)
     */
    public void advanceLoserAsTeam1(TournamentMatch next) {
        Long loserId = getLoserId();
        if (loserId == null) return;
        String name = loserId.equals(team1Id) ? team1Name : team2Name;
        String logo = loserId.equals(team1Id) ? team1LogoUrl : team2LogoUrl;
        next.assignTeam1(loserId, name, logo);
    }

    public void advanceLoserAsTeam2(TournamentMatch next) {
        Long loserId = getLoserId();
        if (loserId == null) return;
        String name = loserId.equals(team1Id) ? team1Name : team2Name;
        String logo = loserId.equals(team1Id) ? team1LogoUrl : team2LogoUrl;
        next.assignTeam2(loserId, name, logo);
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
        return isFinished() && winnerId == null;
    }

    /**
     * 패자 팀 ID 반환
     */
    public Long getLoserId() {
        if (!isFinished() || winnerId == null || team1Id == null || team2Id == null) {
            return null;
        }
        return winnerId.equals(team1Id) ? team2Id : team1Id;
    }

    /**
     * 패자 팀 이름 반환
     */
    public String getLoserName() {
        if (!isFinished() || winnerId == null) return null;
        return winnerId.equals(team1Id) ? team2Name : team1Name;
    }

    /**
     * 패자 팀 로고 반환
     */
    public String getLoserLogoUrl() {
        if (!isFinished() || winnerId == null) return null;
        return winnerId.equals(team1Id) ? team2LogoUrl : team1LogoUrl;
    }
}
