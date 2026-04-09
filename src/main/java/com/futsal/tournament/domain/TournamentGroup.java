package com.futsal.tournament.domain;

import com.futsal.team.domain.Team;
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
     * 조에 속한 팀들
     */
    @ManyToMany
    @JoinTable(
        name = "tournament_group_teams",
        joinColumns = @JoinColumn(name = "group_id"),
        inverseJoinColumns = @JoinColumn(name = "team_id")
    )
    @Builder.Default
    private List<Team> teams = new ArrayList<>();

    // ===== 비즈니스 로직 =====

    /**
     * 팀 추가
     */
    public void addTeam(Team team) {
        if (!teams.contains(team)) {
            teams.add(team);
        }
    }

    /**
     * 팀 제거
     */
    public void removeTeam(Team team) {
        teams.remove(team);
    }

    /**
     * 조가 가득 찼는지 확인
     */
    public boolean isFull(int maxTeamsPerGroup) {
        return teams.size() >= maxTeamsPerGroup;
    }
}
