package com.futsal.tournament.repository;

import com.futsal.tournament.domain.Gender;
import com.futsal.tournament.domain.PlayerType;
import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.dto.TournamentListResponse;
import com.futsal.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {

  // 페이지네이션 + 필터 + 서버 정렬 통합 쿼리
  // 정렬: 진행중(오늘) > 모집중 > 예정 > 종료, 같은 상태면 날짜순
  @Query(value = """
      SELECT new com.futsal.tournament.dto.TournamentListResponse(
          t.id,
          t.title,
          t.tournamentDate,
          t.location,
          t.recruitmentStatus,
          '',
          u.nickname,
          u.profileImageUrl,
          t.gender,
          t.playerType,
          t.isExternal,
          u.verificationStatus
      )
      FROM Tournament t
      LEFT JOIN t.registeredBy u
      WHERE (:gender IS NULL OR t.gender = :gender)
        AND (:playerType IS NULL OR t.playerType = :playerType)
        AND (:recruitmentStatus IS NULL OR t.recruitmentStatus = :recruitmentStatus)
      ORDER BY
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
  Page<TournamentListResponse> findPaged(
      @Param("gender") Gender gender,
      @Param("playerType") PlayerType playerType,
      @Param("recruitmentStatus") String recruitmentStatus,
      Pageable pageable
  );

  // 키워드 검색 페이지네이션
  @Query(value = """
      SELECT new com.futsal.tournament.dto.TournamentListResponse(
          t.id,
          t.title,
          t.tournamentDate,
          t.location,
          t.recruitmentStatus,
          '',
          u.nickname,
          u.profileImageUrl,
          t.gender,
          t.playerType,
          t.isExternal,
          u.verificationStatus
      )
      FROM Tournament t
      LEFT JOIN t.registeredBy u
      WHERE t.title LIKE CONCAT('%', :keyword, '%')
         OR t.location LIKE CONCAT('%', :keyword, '%')
      ORDER BY
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
  Page<TournamentListResponse> findPagedByKeyword(
      @Param("keyword") String keyword,
      Pageable pageable
  );

  // posterUrls만 조회 (id + posterUrls)
  @Query("SELECT t.id, t.posterUrls FROM Tournament t WHERE t.id IN :ids")
  List<Object[]> findPosterUrlsByIds(@Param("ids") List<Long> ids);

  // 사이트맵용 전체 목록 (id, tournamentDate만 필요)
  @Query("""
      SELECT new com.futsal.tournament.dto.TournamentListResponse(
          t.id,
          t.title,
          t.tournamentDate,
          t.location,
          t.recruitmentStatus,
          '',
          u.nickname,
          u.profileImageUrl,
          t.gender,
          t.playerType,
          t.isExternal,
          u.verificationStatus
      )
      FROM Tournament t
      LEFT JOIN t.registeredBy u
      ORDER BY t.tournamentDate ASC
      """)
  List<TournamentListResponse> findListAll();

  // 내가 등록한 대회
  @Query("""
      SELECT new com.futsal.tournament.dto.TournamentListResponse(
          t.id,
          t.title,
          t.tournamentDate,
          t.location,
          t.recruitmentStatus,
          '',
          u.nickname,
          u.profileImageUrl,
          t.gender,
          t.playerType,
          t.isExternal,
          u.verificationStatus
      )
      FROM Tournament t
      LEFT JOIN t.registeredBy u
      WHERE t.registeredBy = :registeredBy
      ORDER BY t.createdAt DESC
      """)
  List<TournamentListResponse> findListByRegisteredBy(@Param("registeredBy") User registeredBy);

  // 참가 코드 관련
  boolean existsByParticipantCode(String participantCode);
  java.util.Optional<Tournament> findByParticipantCode(String participantCode);

  // 운영진 코드 관련
  boolean existsByStaffCode(String staffCode);
  java.util.Optional<Tournament> findByStaffCode(String staffCode);

  // 중복 대회 체크
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
