package com.futsal.tournament.infrastructure;

import com.futsal.tournament.domain.TournamentParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
           "WHERE p.tournamentId = :tournamentId " +
           "AND p.status = 'CONFIRMED' " +
           "ORDER BY p.createdAt ASC")
    List<TournamentParticipant> findByTournamentIdAndConfirmed(@Param("tournamentId") Long tournamentId);

    /**
     * 특정 팀의 참가 여부 확인
     */
    @Query("SELECT p FROM TournamentParticipant p " +
           "WHERE p.tournamentId = :tournamentId " +
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
     * 대회별 확정 참가팀 수 일괄 조회
     */
    @Query("SELECT p.tournamentId, COUNT(p) " +
           "FROM TournamentParticipant p " +
           "WHERE p.tournamentId IN :tournamentIds " +
           "AND p.status = 'CONFIRMED' " +
           "GROUP BY p.tournamentId")
    List<Object[]> countConfirmedByTournamentIds(@Param("tournamentIds") List<Long> tournamentIds);

    /**
     * 사용자가 등록한 팀 목록 조회
     */
    @Query("SELECT p FROM TournamentParticipant p " +
           "WHERE p.registeredBy = :userId " +
           "AND p.status = 'CONFIRMED' " +
           "ORDER BY p.createdAt DESC")
    List<TournamentParticipant> findByRegisteredBy(@Param("userId") Long userId);

    /**
     * 사용자가 참가한 참가 목록 조회 (tournamentId만 담김, 서비스에서 Tournament 별도 로드)
     */
    @Query("SELECT p FROM TournamentParticipant p " +
           "WHERE p.registeredBy = :userId " +
           "AND p.status = 'CONFIRMED' " +
           "ORDER BY p.createdAt DESC")
    List<TournamentParticipant> findByRegisteredByWithTournament(@Param("userId") Long userId);

    /**
     * 팀의 대회 참가 이력 조회
     */
    @Query("SELECT p FROM TournamentParticipant p " +
           "WHERE p.teamId = :teamId " +
           "ORDER BY p.createdAt DESC")
    List<TournamentParticipant> findByTeamIdWithTournament(@Param("teamId") Long teamId);

    /**
     * 팀의 확정된 참가 이력 조회
     */
    @Query("SELECT p FROM TournamentParticipant p " +
           "WHERE p.teamId = :teamId " +
           "AND p.status = 'CONFIRMED' " +
           "ORDER BY p.createdAt DESC")
    List<TournamentParticipant> findConfirmedByTeamId(@Param("teamId") Long teamId);

    /**
     * 대회 삭제 시 참가 기록 일괄 삭제
     */
    void deleteByTournamentId(Long tournamentId);

    /**
     * 사용자 탈퇴 시 등록한 참가 기록 삭제
     */
    void deleteByRegisteredBy(Long userId);

    /**
     * 팀이 현재 CONFIRMED 상태로 참가 중인지 확인 — 팀원 추가 잠금 체크용
     */
    boolean existsByTeamIdAndStatus(
        Long teamId, TournamentParticipant.ParticipantStatus status);

    /**
     * 팀 프로필 변경 시 역정규화 필드 일괄 동기화
     */
    @Modifying
    @Query("UPDATE TournamentParticipant p " +
           "SET p.teamName = :name, p.teamLogoUrl = :logoUrl " +
           "WHERE p.teamId = :teamId")
    void updateTeamProfile(
        @Param("teamId") Long teamId,
        @Param("name") String name,
        @Param("logoUrl") String logoUrl
    );
}
