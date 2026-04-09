package com.futsal.tournament.infrastructure;

import com.futsal.tournament.domain.Bracket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BracketRepository extends JpaRepository<Bracket, Long> {

    Optional<Bracket> findByTournamentId(Long tournamentId);

    boolean existsByTournamentId(Long tournamentId);
}
