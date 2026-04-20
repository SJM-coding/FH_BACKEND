package com.futsal.tournament.application;

import com.futsal.team.domain.Team;
import com.futsal.team.domain.TeamMember;
import com.futsal.team.domain.TeamMemberStatus;
import com.futsal.team.infrastructure.TeamMemberRepository;
import com.futsal.team.infrastructure.TeamRepository;
import com.futsal.tournament.domain.Bracket;
import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.domain.TournamentParticipant;
import com.futsal.tournament.domain.TournamentParticipantMember;
import com.futsal.tournament.infrastructure.BracketRepository;
import com.futsal.tournament.infrastructure.TournamentParticipantMemberRepository;
import com.futsal.tournament.infrastructure.TournamentParticipantRepository;
import com.futsal.tournament.infrastructure.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 대회 참가 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipantService {

    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentParticipantMemberRepository participantMemberRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final BracketRepository bracketRepository;

    /**
     * 운영진 코드 발급 (대회 확정 시 호출)
     * 코드 생성 책임은 Tournament Aggregate → ShareCode VO에 위임한다.
     */
    @Transactional
    public String generateStaffCode(Long tournamentId, Long userId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + tournamentId));

        // 권한 확인
        if (tournament.getRegisteredById() == null
                || !tournament.getRegisteredById().equals(userId)) {
            throw new RuntimeException("대회 주최자만 운영진 코드를 생성할 수 있습니다.");
        }

        // Tournament Aggregate가 코드 발급 (이미 있으면 내부에서 무시)
        tournament.issueStaffCode(
            code -> !tournamentRepository.existsByStaffCode(code));
        tournamentRepository.save(tournament);

        log.info("운영진 코드 생성: 대회 ID={}, 코드={}",
            tournamentId, tournament.getStaffCode());
        return tournament.getStaffCode();
    }

    /**
     * 대회 참가 신청 (참가 코드 사용)
     * 비관적 락으로 maxTeams 초과를 막고, Unique Constraint로 중복 참가를 물리적으로 차단한다.
     */
    @Transactional
    public TournamentParticipant joinTournament(String participantCode, Long teamId, Long userId) {
        // 참가 코드로 대회 ID 조회 후 비관적 락 획득 (동시 요청 직렬화)
        Long tournamentId = tournamentRepository.findByParticipantCode(participantCode)
                .orElseThrow(() -> new RuntimeException("유효하지 않은 참가코드입니다: " + participantCode))
                .getId();
        Tournament tournament = tournamentRepository.findByIdForUpdate(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다."));

        // 대진표 생성 여부 확인 (Bracket aggregate)
        boolean bracketGenerated = bracketRepository.findByTournamentId(tournamentId)
                .map(Bracket::isGenerated).orElse(false);
        if (bracketGenerated) {
            throw new IllegalStateException("대진표가 생성된 후에는 참가 신청이 불가능합니다.");
        }

        // Tournament 상태 기반 불변식 검증
        if (!tournament.isJoinable()) {
            throw new IllegalStateException("현재 대회 참가가 불가능합니다.");
        }

        // maxTeams 초과 검증 (락 안에서 카운트 → 안전)
        long currentCount = participantRepository.countByTournamentIdAndStatus(
            tournament.getId(), TournamentParticipant.ParticipantStatus.CONFIRMED);
        if (currentCount >= tournament.getMaxTeams()) {
            throw new IllegalStateException("참가 팀이 마감되었습니다.");
        }

        // 중복 참가 검증 (Unique Constraint가 최후 보루이지만 명시적으로도 체크)
        participantRepository.findByTournamentIdAndTeamId(tournament.getId(), teamId)
            .ifPresent(p -> { throw new IllegalStateException("이미 참가 중인 팀입니다."); });

        // Cross-BC: Team 정보 조회
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));

        TournamentParticipant participant = TournamentParticipant.builder()
                .tournamentId(tournament.getId())
                .teamId(teamId)
                .teamName(team.getName())
                .teamLogoUrl(team.getLogoUrl())
                .registeredBy(userId)
                .status(TournamentParticipant.ParticipantStatus.CONFIRMED)
                .build();

        TournamentParticipant saved = participantRepository.save(participant);

        // 참가 시점 팀원 스냅샷 저장 — 이후 팀 구성 변경과 무관하게 이력 보존
        List<TeamMember> activeMembers = teamMemberRepository
            .findByTeamIdAndStatusWithUser(teamId, TeamMemberStatus.ACTIVE);
        List<TournamentParticipantMember> snapshots = activeMembers.stream()
            .map(m -> TournamentParticipantMember.builder()
                .tournamentParticipantId(saved.getId())
                .tournamentId(tournament.getId())
                .teamId(teamId)
                .userId(m.getUser().getId())
                .build())
            .toList();
        participantMemberRepository.saveAll(snapshots);

        log.info("대회 참가 완료: 대회 ID={}, 팀 ID={}, 사용자 ID={}, 스냅샷 인원={}",
                tournament.getId(), teamId, userId, snapshots.size());

        return saved;
    }

    /**
     * 참가 취소
     */
    @Transactional
    public void withdrawParticipant(Long tournamentId, Long teamId, Long userId) {
        TournamentParticipant participant = participantRepository
                .findByTournamentIdAndTeamId(tournamentId, teamId)
                .orElseThrow(() -> new RuntimeException("참가 내역을 찾을 수 없습니다."));

        // 권한 확인
        if (!participant.getRegisteredBy().equals(userId)) {
            throw new RuntimeException("참가 신청자만 취소할 수 있습니다.");
        }

        boolean bracketGenerated = bracketRepository.findByTournamentId(tournamentId)
            .map(Bracket::isGenerated).orElse(false);
        if (bracketGenerated) {
            throw new IllegalStateException("대진표가 생성된 후에는 참가 취소가 불가능합니다.");
        }

        // Participant Aggregate가 자신의 상태 변경
        participant.withdraw();
        participantRepository.save(participant);

        // 참가 취소 시 스냅샷도 함께 삭제
        participantMemberRepository.deleteByTournamentParticipantId(participant.getId());

        log.info("대회 참가 취소: 대회 ID={}, 팀 ID={}", tournamentId, teamId);
    }

    /**
     * 대회 참가팀 목록 조회
     */
    @Transactional(readOnly = true)
    public List<TournamentParticipant> getParticipants(Long tournamentId) {
        return participantRepository.findByTournamentIdAndConfirmed(tournamentId);
    }

    /**
     * 내가 참가한 대회 목록 조회
     */
    @Transactional(readOnly = true)
    public List<Tournament> getMyParticipatedTournaments(Long userId) {
        List<TournamentParticipant> participants =
                participantRepository.findByRegisteredByWithTournament(userId);
        List<Long> tournamentIds = participants.stream()
                .map(TournamentParticipant::getTournamentId)
                .distinct()
                .toList();
        return tournamentRepository.findAllById(tournamentIds);
    }

}
