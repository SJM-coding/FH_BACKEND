package com.futsal.team.event;

import com.futsal.team.infrastructure.TeamAwardRepository;
import com.futsal.tournament.event.TournamentTitleChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 대회 제목 변경 이벤트 수신
 * Team BC 내 역정규화 필드(TeamAward.tournamentName)를 일괄 동기화
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TournamentTitleChangedEventListener {

    private final TeamAwardRepository teamAwardRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(TournamentTitleChangedEvent event) {
        log.info("대회 제목 변경 동기화: tournamentId={}, title={}",
            event.tournamentId(), event.title());

        teamAwardRepository.updateTournamentTitle(event.tournamentId(), event.title());
    }
}
