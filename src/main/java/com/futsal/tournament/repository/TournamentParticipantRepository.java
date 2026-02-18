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
}
