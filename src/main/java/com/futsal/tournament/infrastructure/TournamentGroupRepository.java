package com.futsal.tournament.infrastructure;

import com.futsal.tournament.domain.TournamentGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentGroupRepository extends JpaRepository<TournamentGroup, Long> {

    /**
     * 대회별 모든 조 조회
     */
    @Query("SELECT g FROM TournamentGroup g " +
           "LEFT JOIN FETCH g.teams " +
           "WHERE g.tournament.id = :tournamentId " +
           "ORDER BY g.groupOrder ASC")
    List<TournamentGroup> findByTournamentIdWithTeams(@Param("tournamentId") Long tournamentId);

    /**
     * 특정 조 조회
     */
    @Query("SELECT g FROM TournamentGroup g " +
           "LEFT JOIN FETCH g.teams " +
           "WHERE g.tournament.id = :tournamentId " +
           "AND g.groupName = :groupName")
    Optional<TournamentGroup> findByTournamentIdAndGroupName(
        @Param("tournamentId") Long tournamentId, 
        @Param("groupName") String groupName
    );

    /**
     * 대회의 조 개수 조회
     */
    Long countByTournamentId(Long tournamentId);
}
