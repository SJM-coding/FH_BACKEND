package com.futsal.tournament.service;

import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.domain.TournamentParticipant;
import com.futsal.tournament.repository.TournamentParticipantRepository;
import com.futsal.tournament.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

/**
 * 대회 참가 서비스 (공유코드 기반)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipantService {

    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository participantRepository;
    // private final TeamRepository teamRepository; // TODO: 실제 구현 시 추가

    private static final String SHARE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 혼동 없는 문자만
    private static final int SHARE_CODE_LENGTH = 6;

    /**
     * 공유코드 생성
     */
    @Transactional
    public String generateShareCode(Long tournamentId, Long userId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + tournamentId));

        // 권한 확인
        if (tournament.getRegisteredById() == null || !tournament.getRegisteredById().equals(userId)) {
            throw new RuntimeException("대회 주최자만 공유코드를 생성할 수 있습니다.");
        }

        // 이미 공유코드가 있으면 반환
        if (tournament.getShareCode() != null) {
            return tournament.getShareCode();
        }

        // 고유한 공유코드 생성
        String shareCode;
        do {
            shareCode = generateRandomCode();
        } while (tournamentRepository.existsByShareCode(shareCode));

        tournament.setShareCode(shareCode);
        tournamentRepository.save(tournament);

        log.info("공유코드 생성: 대회 ID={}, 코드={}", tournamentId, shareCode);
        return shareCode;
    }

    /**
     * 공유코드로 대회 조회
     */
    @Transactional(readOnly = true)
    public Tournament findByShareCode(String shareCode) {
        return tournamentRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new RuntimeException("유효하지 않은 공유코드입니다: " + shareCode));
    }

    /**
     * 대회 참가 신청
     */
    @Transactional
    public TournamentParticipant joinTournament(String shareCode, Long teamId, Long userId) {
        // 공유코드로 대회 조회
        Tournament tournament = findByShareCode(shareCode);

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

        // TODO: Team 정보 조회
        // Team team = teamRepository.findById(teamId)
        //         .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));
        
        // TODO: 팀장 권한 확인
        // if (!team.getCaptain().getId().equals(userId)) {
        //     throw new RuntimeException("팀장만 대회에 참가 신청할 수 있습니다.");
        // }

        // 참가팀 등록
        TournamentParticipant participant = TournamentParticipant.builder()
                .tournament(tournament)
                .teamId(teamId)
                .teamName("Team " + teamId) // TODO: 실제 팀 이름
                .teamLogoUrl(null) // TODO: 실제 팀 로고
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
     * 랜덤 코드 생성
     */
    private String generateRandomCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder(SHARE_CODE_LENGTH);
        
        for (int i = 0; i < SHARE_CODE_LENGTH; i++) {
            int index = random.nextInt(SHARE_CODE_CHARS.length());
            code.append(SHARE_CODE_CHARS.charAt(index));
        }
        
        return code.toString();
    }
}
