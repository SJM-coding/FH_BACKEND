package com.futsal.tournament.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 조별 리그의 조 (Group)
 */
@Entity
@Table(name = "tournament_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TournamentGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    /**
     * 조 이름 (A, B, C, D...)
     */
    @Column(nullable = false, length = 10)
    private String groupName;

    /**
     * 조 순서
     */
    @Column(nullable = false)
    private Integer groupOrder;

    /**
     * 조에 속한 팀 ID 목록
     */
    @ElementCollection
    @CollectionTable(
        name = "tournament_group_teams",
        joinColumns = @JoinColumn(name = "group_id")
    )
    @Column(name = "team_id")
    @Builder.Default
    private List<Long> teamIds = new ArrayList<>();

    // ===== 비즈니스 로직 =====

    public void addTeamId(Long teamId) {
        if (!teamIds.contains(teamId)) {
            teamIds.add(teamId);
        }
    }

    public void removeTeamId(Long teamId) {
        teamIds.remove(teamId);
    }

    public boolean isFull(int maxTeamsPerGroup) {
        return teamIds.size() >= maxTeamsPerGroup;
    }
}
