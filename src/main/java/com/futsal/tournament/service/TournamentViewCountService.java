package com.futsal.tournament.service;

import com.futsal.tournament.domain.Tournament;

public interface TournamentViewCountService {

    int recordViewAndGetVisibleCount(Tournament tournament);
}
