package com.futsal.tournament.infrastructure;

import com.futsal.tournament.domain.TournamentResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentResultRepository extends JpaRepository<TournamentResult, Long> {

    /**
     * 대회별 결과 조회 (순위순)
     */
    @Query("SELECT r FROM TournamentResult r WHERE r.tournament.id = :tournamentId ORDER BY r.rank ASC")
    List<TournamentResult> findByTournamentId(@Param("tournamentId") Long tournamentId);

    /**
     * 대회 결과 존재 여부
     */
    boolean existsByTournamentId(Long tournamentId);

    /**
     * 대회 결과 삭제
     */
    void deleteByTournamentId(Long tournamentId);
}
