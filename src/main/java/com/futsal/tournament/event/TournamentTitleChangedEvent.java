package com.futsal.tournament.event;

/**
 * 대회 제목 변경 이벤트
 * Team BC에서 구독해 TeamAward.tournamentName을 동기화한다.
 */
public record TournamentTitleChangedEvent(
    Long tournamentId,
    String title
) {}
