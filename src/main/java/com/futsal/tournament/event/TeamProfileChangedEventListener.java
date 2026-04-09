package com.futsal.tournament.event;

import com.futsal.team.event.TeamProfileChangedEvent;
import com.futsal.tournament.infrastructure.TournamentMatchRepository;
import com.futsal.tournament.infrastructure.TournamentParticipantRepository;
import com.futsal.tournament.infrastructure.TournamentResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 팀 프로필 변경 이벤트 수신
 * Tournament BC 내 역정규화 필드(teamName, teamLogoUrl)를 일괄 동기화
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamProfileChangedEventListener {

    private final TournamentParticipantRepository participantRepository;
    private final TournamentResultRepository resultRepository;
    private final TournamentMatchRepository matchRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(TeamProfileChangedEvent event) {
        log.info("팀 프로필 변경 동기화: teamId={}, name={}", event.teamId(), event.name());

        participantRepository.updateTeamProfile(
            event.teamId(), event.name(), event.logoUrl());

        resultRepository.updateTeamName(event.teamId(), event.name());

        matchRepository.updateTeam1Profile(event.teamId(), event.name(), event.logoUrl());
        matchRepository.updateTeam2Profile(event.teamId(), event.name(), event.logoUrl());
        matchRepository.updateWinnerName(event.teamId(), event.name());
    }
}
