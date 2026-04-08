package com.futsal.tournament.application;

import com.futsal.team.domain.Team;
import com.futsal.team.infrastructure.TeamRepository;
import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.domain.TournamentParticipant;
import com.futsal.tournament.infrastructure.TournamentParticipantRepository;
import com.futsal.tournament.infrastructure.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
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
    private final TournamentService tournamentService;
    private final TeamRepository teamRepository;

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    /**
     * 운영진 코드 생성 (대회 확정 시 호출)
     */
    @Transactional
    public String generateStaffCode(Long tournamentId, Long userId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + tournamentId));

        // 권한 확인
        if (tournament.getRegisteredById() == null || !tournament.getRegisteredById().equals(userId)) {
            throw new RuntimeException("대회 주최자만 운영진 코드를 생성할 수 있습니다.");
        }

        // 이미 운영진 코드가 있으면 반환
        if (tournament.getStaffCode() != null) {
            return tournament.getStaffCode();
        }

        // 고유한 운영진 코드 생성
        String staffCode = tournamentService.generateUniqueStaffCode();
        tournament.setStaffCode(staffCode);
        tournamentRepository.save(tournament);

        log.info("운영진 코드 생성: 대회 ID={}, 코드={}", tournamentId, staffCode);
        return staffCode;
    }

    /**
     * 참가 코드로 대회 조회 (참가 신청용)
     */
    @Transactional(readOnly = true)
    public Tournament findByParticipantCode(String participantCode) {
        return tournamentRepository.findByParticipantCode(participantCode)
                .orElseThrow(() -> new RuntimeException("유효하지 않은 참가코드입니다: " + participantCode));
    }

    /**
     * 운영진 코드로 대회 조회 (점수 입력용)
     */
    @Transactional(readOnly = true)
    public Tournament findByStaffCode(String staffCode) {
        return tournamentRepository.findByStaffCode(staffCode)
                .orElseThrow(() -> new RuntimeException("유효하지 않은 운영진코드입니다: " + staffCode));
    }

    /**
     * 대회 참가 신청 (참가 코드 사용)
     */
    @Transactional
    public TournamentParticipant joinTournament(String participantCode, Long teamId, Long userId) {
        // 참가 코드로 대회 조회
        Tournament tournament = findByParticipantCode(participantCode);

        // 참가 가능 여부 확인
        if (!Boolean.TRUE.equals(tournament.getAllowJoin())) {
            throw new RuntimeException("현재 대회 참가가 마감되었습니다.");
        }

        // 모집 상태 확인
        if (!"OPEN".equalsIgnoreCase(tournament.getRecruitmentStatus())) {
            throw new RuntimeException("모집이 마감된 대회입니다.");
        }

        // 최대 팀 수 확인
        long currentCount = participantRepository.countByTournamentIdAndStatus(
            tournament.getId(),
            TournamentParticipant.ParticipantStatus.CONFIRMED
        );
        if (currentCount >= tournament.getMaxTeams()) {
            throw new RuntimeException("참가 팀이 마감되었습니다.");
        }

        // 중복 참가 확인
        participantRepository.findByTournamentIdAndTeamId(tournament.getId(), teamId)
                .ifPresent(p -> {
                    throw new RuntimeException("이미 참가한 팀입니다.");
                });

        // 팀 정보 조회
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));

        // 참가팀 등록
        TournamentParticipant participant = TournamentParticipant.builder()
                .tournament(tournament)
                .teamId(teamId)
                .teamName(team.getName())
                .teamLogoUrl(team.getLogoUrl())
                .registeredBy(userId)
                .status(TournamentParticipant.ParticipantStatus.CONFIRMED)
                .build();

        TournamentParticipant saved = participantRepository.save(participant);

        log.info("대회 참가 완료: 대회 ID={}, 팀 ID={}, 사용자 ID={}",
                tournament.getId(), teamId, userId);

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

        // 대진표 생성 후에는 취소 불가
        if (participant.getTournament().getBracketGenerated()) {
            throw new RuntimeException("대진표가 생성된 후에는 참가 취소가 불가능합니다.");
        }

        participant.withdraw();
        participantRepository.save(participant);

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
        List<TournamentParticipant> participants = participantRepository.findByRegisteredByWithTournament(userId);
        return participants.stream()
                .map(TournamentParticipant::getTournament)
                .toList();
    }
}
