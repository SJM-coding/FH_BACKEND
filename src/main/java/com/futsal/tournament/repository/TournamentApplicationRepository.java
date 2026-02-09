package com.futsal.tournament.repository;

import com.futsal.tournament.domain.TournamentApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentApplicationRepository extends JpaRepository<TournamentApplication, Long> {
}
