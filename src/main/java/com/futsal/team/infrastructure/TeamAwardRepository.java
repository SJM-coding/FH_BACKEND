package com.futsal.team.infrastructure;

import com.futsal.team.domain.TeamAward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamAwardRepository extends JpaRepository<TeamAward, Long> {

    /**
     * 팀의 수상 경력 조회 (최신순)
     */
    @Query("SELECT a FROM TeamAward a WHERE a.team.id = :teamId ORDER BY a.awardDate DESC")
    List<TeamAward> findByTeamId(@Param("teamId") Long teamId);

    /**
     * 팀의 수상 경력 개수
     */
    Long countByTeamId(Long teamId);

    /**
     * 대회별 수상 경력 삭제 (대회 결과 삭제 시 사용)
     */
    void deleteByTournamentId(Long tournamentId);
}
