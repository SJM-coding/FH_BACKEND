package com.futsal.tournament.repository;

import com.futsal.tournament.domain.Tournament;
import com.futsal.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
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

    // Phase 2-5: 특정 날짜 이전 대회 조회 (자동 삭제용)
    List<Tournament> findByTournamentDateBefore(LocalDate date);

}
