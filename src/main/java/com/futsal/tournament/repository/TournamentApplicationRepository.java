package com.futsal.tournament.repository;

import com.futsal.tournament.domain.ApplicationStatus;
import com.futsal.tournament.domain.TournamentApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentApplicationRepository extends JpaRepository<TournamentApplication, Long> {

    /**
     * 대회별 신청서 목록 조회
     */
    @Query("SELECT ta FROM TournamentApplication ta " +
           "JOIN FETCH ta.team " +
           "JOIN FETCH ta.applicant " +
           "WHERE ta.tournament.id = :tournamentId " +
           "ORDER BY ta.appliedAt DESC")
    List<TournamentApplication> findByTournamentIdWithDetails(@Param("tournamentId") Long tournamentId);

    /**
     * 팀별 신청서 목록 조회
     */
    @Query("SELECT ta FROM TournamentApplication ta " +
           "JOIN FETCH ta.tournament " +
           "WHERE ta.team.id = :teamId " +
           "ORDER BY ta.appliedAt DESC")
    List<TournamentApplication> findByTeamIdWithDetails(@Param("teamId") Long teamId);

    /**
     * 대회 + 팀 조합으로 신청서 조회 (중복 신청 방지)
     */
    Optional<TournamentApplication> findByTournamentIdAndTeamId(Long tournamentId, Long teamId);

    /**
     * 대회별 상태별 신청서 수 조회
     */
    Long countByTournamentIdAndStatus(Long tournamentId, ApplicationStatus status);

    /**
     * 신청자(유저)별 신청서 조회
     */
    @Query("SELECT ta FROM TournamentApplication ta " +
           "JOIN FETCH ta.tournament " +
           "JOIN FETCH ta.team " +
           "WHERE ta.applicant.id = :userId " +
           "ORDER BY ta.appliedAt DESC")
    List<TournamentApplication> findByApplicantIdWithDetails(@Param("userId") Long userId);

    /**
     * 특정 신청서 상세 조회 (모든 관계 Fetch)
     */
    @Query("SELECT ta FROM TournamentApplication ta " +
           "JOIN FETCH ta.tournament " +
           "JOIN FETCH ta.team " +
           "JOIN FETCH ta.applicant " +
           "WHERE ta.id = :id")
    Optional<TournamentApplication> findByIdWithDetails(@Param("id") Long id);
}
