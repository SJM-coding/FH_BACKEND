package com.futsal.tournament.infrastructure;

import com.futsal.tournament.domain.TournamentMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentMatchRepository extends JpaRepository<TournamentMatch, Long> {

    /**
     * 대회별 모든 경기 조회
     */
    @Query("SELECT m FROM TournamentMatch m " +
           "WHERE m.tournamentId = :tournamentId " +
           "ORDER BY m.round ASC, m.matchNumber ASC")
    List<TournamentMatch> findByTournamentIdWithTeams(@Param("tournamentId") Long tournamentId);

    /**
     * 특정 라운드의 경기 조회
     */
    @Query("SELECT m FROM TournamentMatch m " +
           "WHERE m.tournamentId = :tournamentId AND m.round = :round " +
           "ORDER BY m.matchNumber ASC")
    List<TournamentMatch> findByTournamentIdAndRound(
        @Param("tournamentId") Long tournamentId,
        @Param("round") Integer round
    );

    /**
     * 특정 조의 경기 조회 (조별리그)
     */
    @Query("SELECT m FROM TournamentMatch m " +
           "WHERE m.tournamentId = :tournamentId AND m.groupId = :groupId " +
           "ORDER BY m.matchNumber ASC")
    List<TournamentMatch> findByTournamentIdAndGroupId(
        @Param("tournamentId") Long tournamentId,
        @Param("groupId") String groupId
    );

    /**
     * 특정 팀의 경기 조회
     */
    @Query("SELECT m FROM TournamentMatch m " +
           "WHERE m.tournamentId = :tournamentId " +
           "AND (m.team1Id = :teamId OR m.team2Id = :teamId) " +
           "ORDER BY m.round ASC, m.matchNumber ASC")
    List<TournamentMatch> findByTournamentIdAndTeamId(
        @Param("tournamentId") Long tournamentId,
        @Param("teamId") Long teamId
    );

    /**
     * 대회의 최대 라운드 조회
     */
    @Query("SELECT MAX(m.round) FROM TournamentMatch m WHERE m.tournamentId = :tournamentId")
    Integer findMaxRoundByTournamentId(@Param("tournamentId") Long tournamentId);

    /**
     * 특정 라운드 완료 여부 확인
     */
    @Query("SELECT COUNT(m) = 0 FROM TournamentMatch m " +
           "WHERE m.tournamentId = :tournamentId " +
           "AND m.round = :round " +
           "AND m.status != 'FINISHED'")
    boolean isRoundCompleted(
        @Param("tournamentId") Long tournamentId,
        @Param("round") Integer round
    );

    /**
     * 다음 경기 조회 (아직 시작 안한 것 중 가장 빠른 것)
     */
    @Query("SELECT m FROM TournamentMatch m " +
           "WHERE m.tournamentId = :tournamentId " +
           "AND m.status = 'SCHEDULED' " +
           "ORDER BY m.round ASC, m.matchNumber ASC")
    List<TournamentMatch> findUpcomingMatches(@Param("tournamentId") Long tournamentId);

    /**
     * 대회의 모든 경기 삭제
     */
    void deleteByTournamentId(Long tournamentId);

    /**
     * 팀 프로필 변경 시 역정규화 필드 일괄 동기화
     */
    @Modifying
    @Query("UPDATE TournamentMatch m " +
           "SET m.team1Name = :name, m.team1LogoUrl = :logoUrl " +
           "WHERE m.team1Id = :teamId")
    void updateTeam1Profile(
        @Param("teamId") Long teamId,
        @Param("name") String name,
        @Param("logoUrl") String logoUrl
    );

    @Modifying
    @Query("UPDATE TournamentMatch m " +
           "SET m.team2Name = :name, m.team2LogoUrl = :logoUrl " +
           "WHERE m.team2Id = :teamId")
    void updateTeam2Profile(
        @Param("teamId") Long teamId,
        @Param("name") String name,
        @Param("logoUrl") String logoUrl
    );

    @Modifying
    @Query("UPDATE TournamentMatch m " +
           "SET m.winnerName = :name " +
           "WHERE m.winnerId = :teamId")
    void updateWinnerName(@Param("teamId") Long teamId, @Param("name") String name);
}
