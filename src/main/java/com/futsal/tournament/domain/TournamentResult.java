package com.futsal.tournament.domain;

import com.futsal.team.domain.AwardType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 대회 결과 (순위)
 * 대회 종료 후 개최자가 입력하면 자동으로 팀 수상 경력에 반영됨
 */
@Entity
@Table(name = "tournament_results",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tournament_id", "team_id"}),
        @UniqueConstraint(columnNames = {"tournament_id", "rank"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "team_name", nullable = false)
    private String teamName;

    /**
     * 순위 (1 = 우승, 2 = 준우승, 3 = 3위, 4 = 4위)
     */
    @Column(name = "rank", nullable = false)
    private Integer rank;

    /**
     * 순위에 해당하는 수상 타입
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "award_type", nullable = false)
    private AwardType awardType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 순위에 따른 AwardType 반환
     */
    public static AwardType getAwardTypeByRank(int rank) {
        return switch (rank) {
            case 1 -> AwardType.CHAMPION;
            case 2 -> AwardType.RUNNER_UP;
            case 3 -> AwardType.THIRD_PLACE;
            case 4 -> AwardType.FOURTH_PLACE;
            default -> null;
        };
    }
}
