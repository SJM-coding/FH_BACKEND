package com.futsal.tournament.infrastructure;

import com.futsal.tournament.domain.Gender;
import com.futsal.tournament.domain.PlayerType;
import com.futsal.tournament.domain.Tournament;
import com.futsal.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {

  /**
   * 페이지네이션 + 필터 + 서버 정렬 (진행중 > 모집중 > 예정 > 종료)
   * registeredBy를 FETCH JOIN 해서 N+1 방지
   */
  @Query(value = """
      SELECT t
      FROM Tournament t
      LEFT JOIN FETCH t.registeredBy u
      WHERE (:gender IS NULL OR t.gender = :gender)
        AND (:playerType IS NULL OR t.playerType = :playerType)
        AND (:recruitmentStatus IS NULL OR t.recruitmentStatus = :recruitmentStatus)
      ORDER BY
          CASE
              WHEN t.isExternal = false
               AND t.recruitmentStatus = 'OPEN'
               AND t.maxTeams > 0
               AND (
                   t.maxTeams - (
                       SELECT COUNT(p)
                       FROM TournamentParticipant p
                       WHERE p.tournamentId = t.id
                         AND p.status = 'CONFIRMED'
                   )
               ) BETWEEN 1 AND 4 THEN 0
              WHEN t.isExternal = false
               AND t.recruitmentStatus = 'OPEN'
               AND t.maxTeams > 0
               AND (
                   SELECT COUNT(p)
                   FROM TournamentParticipant p
                   WHERE p.tournamentId = t.id
                     AND p.status = 'CONFIRMED'
               ) >= (t.maxTeams + 1) / 2 THEN 1
              WHEN t.isExternal = false
               AND t.recruitmentStatus = 'OPEN'
               AND t.createdAt >= :newThreshold THEN 2
              ELSE 3
          END ASC,
          CASE
              WHEN t.tournamentDate = CURRENT_DATE THEN 0
              WHEN t.recruitmentStatus = 'OPEN'    THEN 1
              WHEN t.tournamentDate < CURRENT_DATE THEN 3
              ELSE 2
          END ASC,
          t.tournamentDate ASC
      """,
      countQuery = """
      SELECT COUNT(t)
      FROM Tournament t
      WHERE (:gender IS NULL OR t.gender = :gender)
        AND (:playerType IS NULL OR t.playerType = :playerType)
        AND (:recruitmentStatus IS NULL OR t.recruitmentStatus = :recruitmentStatus)
      """)
  Page<Tournament> findPaged(
      @Param("gender") Gender gender,
      @Param("playerType") PlayerType playerType,
      @Param("recruitmentStatus") String recruitmentStatus,
      @Param("newThreshold") LocalDateTime newThreshold,
      Pageable pageable
  );

  /**
   * 키워드 검색 페이지네이션
   */
  @Query(value = """
      SELECT t
      FROM Tournament t
      LEFT JOIN FETCH t.registeredBy
      WHERE t.title LIKE CONCAT('%', :keyword, '%')
         OR t.location LIKE CONCAT('%', :keyword, '%')
      ORDER BY
          CASE
              WHEN t.isExternal = false
               AND t.recruitmentStatus = 'OPEN'
               AND t.maxTeams > 0
               AND (
                   t.maxTeams - (
                       SELECT COUNT(p)
                       FROM TournamentParticipant p
                       WHERE p.tournamentId = t.id
                         AND p.status = 'CONFIRMED'
                   )
               ) BETWEEN 1 AND 4 THEN 0
              WHEN t.isExternal = false
               AND t.recruitmentStatus = 'OPEN'
               AND t.maxTeams > 0
               AND (
                   SELECT COUNT(p)
                   FROM TournamentParticipant p
                   WHERE p.tournamentId = t.id
                     AND p.status = 'CONFIRMED'
               ) >= (t.maxTeams + 1) / 2 THEN 1
              WHEN t.isExternal = false
               AND t.recruitmentStatus = 'OPEN'
               AND t.createdAt >= :newThreshold THEN 2
              ELSE 3
          END ASC,
          CASE
              WHEN t.tournamentDate = CURRENT_DATE THEN 0
              WHEN t.recruitmentStatus = 'OPEN'    THEN 1
              WHEN t.tournamentDate < CURRENT_DATE THEN 3
              ELSE 2
          END ASC,
          t.tournamentDate ASC
      """,
      countQuery = """
      SELECT COUNT(t)
      FROM Tournament t
      WHERE t.title LIKE CONCAT('%', :keyword, '%')
         OR t.location LIKE CONCAT('%', :keyword, '%')
      """)
  Page<Tournament> findPagedByKeyword(
      @Param("keyword") String keyword,
      @Param("newThreshold") LocalDateTime newThreshold,
      Pageable pageable
  );

  /**
   * posterUrls만 조회 (id + posterUrls)
   */
  @Query("SELECT t.id, t.posterUrls FROM Tournament t WHERE t.id IN :ids")
  List<Object[]> findPosterUrlsByIds(@Param("ids") List<Long> ids);

  /**
   * 사이트맵용 전체 목록
   */
  @Query("""
      SELECT t
      FROM Tournament t
      LEFT JOIN FETCH t.registeredBy
      ORDER BY t.tournamentDate ASC
      """)
  List<Tournament> findListAll();

  /**
   * 내가 등록한 대회 목록
   */
  @Query("""
      SELECT t
      FROM Tournament t
      LEFT JOIN FETCH t.registeredBy
      WHERE t.registeredBy = :registeredBy
      ORDER BY t.createdAt DESC
      """)
  List<Tournament> findListByRegisteredBy(@Param("registeredBy") User registeredBy);

  @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
         "FROM Tournament t WHERE t.shareCode.participantCode = :code")
  boolean existsByParticipantCode(@Param("code") String participantCode);

  @Query("SELECT t FROM Tournament t WHERE t.shareCode.participantCode = :code")
  java.util.Optional<Tournament> findByParticipantCode(@Param("code") String participantCode);

  /**
   * 참가 신청 시 maxTeams 초과 방지용 비관적 락
   * 같은 대회에 대한 동시 참가 요청을 직렬화한다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM Tournament t WHERE t.id = :id")
  java.util.Optional<Tournament> findByIdForUpdate(@Param("id") Long id);

  @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
         "FROM Tournament t WHERE t.shareCode.staffCode = :code")
  boolean existsByStaffCode(@Param("code") String staffCode);

  @Query("SELECT t FROM Tournament t WHERE t.shareCode.staffCode = :code")
  java.util.Optional<Tournament> findByStaffCode(@Param("code") String staffCode);

  boolean existsByTitleAndTournamentDateAndRegisteredBy(
      String title,
      LocalDate tournamentDate,
      User registeredBy
  );

  @Modifying
  @Query("UPDATE Tournament t SET t.viewCount = t.viewCount + 1 WHERE t.id = :id")
  void incrementViewCount(@Param("id") Long id);

  @Modifying
  @Query("UPDATE Tournament t SET t.viewCount = t.viewCount + :count WHERE t.id = :id")
  void incrementViewCountBy(@Param("id") Long id, @Param("count") long count);
}
