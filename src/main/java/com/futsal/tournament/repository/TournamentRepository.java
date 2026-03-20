package com.futsal.tournament.repository;

import com.futsal.tournament.domain.Gender;
import com.futsal.tournament.domain.PlayerType;
import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.dto.TournamentListResponse;
import com.futsal.user.domain.User;
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

    // 키워드 검색 (제목 또는 장소에 키워드 포함)
    @Query("SELECT t FROM Tournament t LEFT JOIN FETCH t.registeredBy WHERE t.title LIKE CONCAT('%', :keyword, '%') OR t.location LIKE CONCAT('%', :keyword, '%') ORDER BY t.tournamentDate ASC")
    List<Tournament> findByKeyword(@Param("keyword") String keyword);

    // 날짜순 전체 조회
    @Query("SELECT t FROM Tournament t LEFT JOIN FETCH t.registeredBy ORDER BY t.tournamentDate ASC")
    List<Tournament> findAllByOrderByTournamentDateAsc();

    // Phase 2-3: 특정 사용자가 등록한 대회 조회
    @Query("SELECT t FROM Tournament t LEFT JOIN FETCH t.registeredBy WHERE t.registeredBy = :registeredBy ORDER BY t.createdAt DESC")
    List<Tournament> findByRegisteredByOrderByCreatedAtDesc(@Param("registeredBy") User registeredBy);

    // 목록용 프로젝션: 전체 조회
    @Query("""
        SELECT new com.futsal.tournament.dto.TournamentListResponse(
            t.id,
            t.title,
            t.tournamentDate,
            t.location,
            t.recruitmentStatus,
            '',
            u.nickname,
            t.gender,
            t.playerType
        )
        FROM Tournament t
        LEFT JOIN t.registeredBy u
        ORDER BY t.tournamentDate ASC
    """)
    List<TournamentListResponse> findListAll();

    // 목록용 프로젝션: 키워드 검색
    @Query("""
        SELECT new com.futsal.tournament.dto.TournamentListResponse(
            t.id,
            t.title,
            t.tournamentDate,
            t.location,
            t.recruitmentStatus,
            '',
            u.nickname,
            t.gender,
            t.playerType
        )
        FROM Tournament t
        LEFT JOIN t.registeredBy u
        WHERE t.title LIKE CONCAT('%', :keyword, '%')
           OR t.location LIKE CONCAT('%', :keyword, '%')
        ORDER BY t.tournamentDate ASC
    """)
    List<TournamentListResponse> findListByKeyword(@Param("keyword") String keyword);

    // 목록용 프로젝션: 내가 등록한 대회
    @Query("""
        SELECT new com.futsal.tournament.dto.TournamentListResponse(
            t.id,
            t.title,
            t.tournamentDate,
            t.location,
            t.recruitmentStatus,
            '',
            u.nickname,
            t.gender,
            t.playerType
        )
        FROM Tournament t
        LEFT JOIN t.registeredBy u
        WHERE t.registeredBy = :registeredBy
        ORDER BY t.createdAt DESC
    """)
    List<TournamentListResponse> findListByRegisteredBy(@Param("registeredBy") User registeredBy);

    // 목록용 프로젝션: gender/playerType 필터
    @Query("""
        SELECT new com.futsal.tournament.dto.TournamentListResponse(
            t.id,
            t.title,
            t.tournamentDate,
            t.location,
            t.recruitmentStatus,
            '',
            u.nickname,
            t.gender,
            t.playerType
        )
        FROM Tournament t
        LEFT JOIN t.registeredBy u
        WHERE t.gender = :gender
        ORDER BY t.tournamentDate ASC
    """)
    List<TournamentListResponse> findListByGender(@Param("gender") Gender gender);

    @Query("""
        SELECT new com.futsal.tournament.dto.TournamentListResponse(
            t.id,
            t.title,
            t.tournamentDate,
            t.location,
            t.recruitmentStatus,
            '',
            u.nickname,
            t.gender,
            t.playerType
        )
        FROM Tournament t
        LEFT JOIN t.registeredBy u
        WHERE t.playerType = :playerType
        ORDER BY t.tournamentDate ASC
    """)
    List<TournamentListResponse> findListByPlayerType(@Param("playerType") PlayerType playerType);

    @Query("""
        SELECT new com.futsal.tournament.dto.TournamentListResponse(
            t.id,
            t.title,
            t.tournamentDate,
            t.location,
            t.recruitmentStatus,
            '',
            u.nickname,
            t.gender,
            t.playerType
        )
        FROM Tournament t
        LEFT JOIN t.registeredBy u
        WHERE t.gender = :gender
          AND t.playerType = :playerType
        ORDER BY t.tournamentDate ASC
    """)
    List<TournamentListResponse> findListByGenderAndPlayerType(
            @Param("gender") Gender gender,
            @Param("playerType") PlayerType playerType
    );

    // LIMIT 적용 버전 (Pageable 사용)
    @Query("""
        SELECT new com.futsal.tournament.dto.TournamentListResponse(
            t.id,
            t.title,
            t.tournamentDate,
            t.location,
            t.recruitmentStatus,
            '',
            u.nickname,
            t.gender,
            t.playerType
        )
        FROM Tournament t
        LEFT JOIN t.registeredBy u
        WHERE t.gender = :gender
          AND t.playerType = :playerType
          AND t.tournamentDate >= CURRENT_DATE
        ORDER BY t.tournamentDate ASC
    """)
    List<TournamentListResponse> findListByGenderAndPlayerTypeWithLimit(
            @Param("gender") Gender gender,
            @Param("playerType") PlayerType playerType,
            Pageable pageable
    );

    // LIMIT 적용 버전 (Gender만)
    @Query("""
        SELECT new com.futsal.tournament.dto.TournamentListResponse(
            t.id,
            t.title,
            t.tournamentDate,
            t.location,
            t.recruitmentStatus,
            '',
            u.nickname,
            t.gender,
            t.playerType
        )
        FROM Tournament t
        LEFT JOIN t.registeredBy u
        WHERE t.gender = :gender
          AND t.tournamentDate >= CURRENT_DATE
        ORDER BY t.tournamentDate ASC
    """)
    List<TournamentListResponse> findListByGenderWithLimit(
            @Param("gender") Gender gender,
            Pageable pageable
    );

    // Phase 2-5: 특정 날짜 이전 대회 조회 (자동 삭제용)
    List<Tournament> findByTournamentDateBefore(LocalDate date);

    // 참가 코드 관련
    boolean existsByParticipantCode(String participantCode);
    java.util.Optional<Tournament> findByParticipantCode(String participantCode);

    // 운영진 코드 관련
    boolean existsByStaffCode(String staffCode);
    java.util.Optional<Tournament> findByStaffCode(String staffCode);

    @Query("SELECT t.id, p FROM Tournament t JOIN t.posterUrls p WHERE t.id IN :ids")
    List<Object[]> findPosterUrlsByTournamentIds(@Param("ids") List<Long> ids);

    // 중복 대회 체크
    boolean existsByTitleAndTournamentDateAndRegisteredBy(
        String title,
        LocalDate tournamentDate,
        User registeredBy
    );


    @Modifying
    @Query("UPDATE Tournament t SET t.viewCount = t.viewCount + 1 WHERE t.id = :id")
    void incrementViewCount(@Param("id") Long id);

}
