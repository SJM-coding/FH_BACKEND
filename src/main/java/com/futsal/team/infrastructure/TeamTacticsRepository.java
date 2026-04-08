package com.futsal.team.infrastructure;

import com.futsal.team.domain.Team;
import com.futsal.team.domain.TeamTactics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamTacticsRepository extends JpaRepository<TeamTactics, Long> {
    
    /**
     * 팀의 전술 조회
     */
    Optional<TeamTactics> findByTeam(Team team);
    
    /**
     * 팀 ID로 전술 조회
     */
    Optional<TeamTactics> findByTeamId(Long teamId);
}
