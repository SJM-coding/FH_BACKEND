package com.futsal.tournament.application;

import com.futsal.tournament.domain.Tournament;

public interface TournamentViewCountService {

    int recordViewAndGetVisibleCount(Tournament tournament);
}
