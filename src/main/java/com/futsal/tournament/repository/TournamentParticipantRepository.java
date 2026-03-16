package com.futsal.tournament.repository;

import com.futsal.tournament.domain.TournamentParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentParticipantRepository extends JpaRepository<TournamentParticipant, Long> {

    /**
     * 대회별 참가팀 조회 (확정된 팀만)
     */
    @Query("SELECT p FROM TournamentParticipant p " +
           "WHERE p.tournament.id = :tournamentId " +
           "AND p.status = 'CONFIRMED' " +
           "ORDER BY p.createdAt ASC")
    List<TournamentParticipant> findByTournamentIdAndConfirmed(@Param("tournamentId") Long tournamentId);

    /**
     * 특정 팀의 참가 여부 확인
     */
    @Query("SELECT p FROM TournamentParticipant p " +
           "WHERE p.tournament.id = :tournamentId " +
           "AND p.teamId = :teamId " +
           "AND p.status = 'CONFIRMED'")
    Optional<TournamentParticipant> findByTournamentIdAndTeamId(
        @Param("tournamentId") Long tournamentId,
        @Param("teamId") Long teamId
    );

    /**
     * 대회 참가팀 수 조회
     */
    Long countByTournamentIdAndStatus(Long tournamentId, TournamentParticipant.ParticipantStatus status);

    /**
     * 사용자가 등록한 팀 목록 조회
     */
    @Query("SELECT p FROM TournamentParticipant p " +
           "WHERE p.registeredBy = :userId " +
           "AND p.status = 'CONFIRMED' " +
           "ORDER BY p.createdAt DESC")
    List<TournamentParticipant> findByRegisteredBy(@Param("userId") Long userId);

    /**
     * 사용자가 참가한 대회 목록 조회 (fetch join으로 Tournament와 registeredBy 로드)
     */
    @Query("SELECT p FROM TournamentParticipant p " +
           "JOIN FETCH p.tournament t " +
           "LEFT JOIN FETCH t.registeredBy " +
           "WHERE p.registeredBy = :userId " +
           "AND p.status = 'CONFIRMED' " +
           "ORDER BY t.tournamentDate DESC")
    List<TournamentParticipant> findByRegisteredByWithTournament(@Param("userId") Long userId);

    /**
     * 팀의 대회 참가 이력 조회 (최신순)
     */
    @Query("SELECT p FROM TournamentParticipant p " +
           "JOIN FETCH p.tournament t " +
           "WHERE p.teamId = :teamId " +
           "ORDER BY t.tournamentDate DESC")
    List<TournamentParticipant> findByTeamIdWithTournament(@Param("teamId") Long teamId);

    /**
     * 팀의 확정된 참가 이력 조회
     */
    @Query("SELECT p FROM TournamentParticipant p " +
           "JOIN FETCH p.tournament t " +
           "WHERE p.teamId = :teamId " +
           "AND p.status = 'CONFIRMED' " +
           "ORDER BY t.tournamentDate DESC")
    List<TournamentParticipant> findConfirmedByTeamId(@Param("teamId") Long teamId);
}
