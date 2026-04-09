package com.futsal.team.infrastructure;

import com.futsal.team.domain.TeamAward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 팀 삭제 시 수상 경력 일괄 삭제
     */
    @Modifying
    @Query("DELETE FROM TeamAward a WHERE a.team.id = :teamId")
    void deleteByTeamId(@Param("teamId") Long teamId);

    /**
     * 대회 제목 변경 시 역정규화 필드 일괄 동기화
     */
    @Modifying
    @Query("UPDATE TeamAward a SET a.tournamentName = :title WHERE a.tournamentId = :tournamentId")
    void updateTournamentTitle(
        @Param("tournamentId") Long tournamentId,
        @Param("title") String title
    );
}
