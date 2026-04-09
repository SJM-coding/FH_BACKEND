package com.futsal.user.application;

import com.futsal.team.domain.Team;
import com.futsal.team.infrastructure.TeamAwardRepository;
import com.futsal.team.infrastructure.TeamMemberRepository;
import com.futsal.team.infrastructure.TeamRepository;
import com.futsal.tournament.infrastructure.TournamentGroupRepository;
import com.futsal.tournament.infrastructure.TournamentMatchRepository;
import com.futsal.tournament.infrastructure.TournamentParticipantRepository;
import com.futsal.tournament.infrastructure.TournamentRepository;
import com.futsal.tournament.infrastructure.TournamentResultRepository;
import com.futsal.tournament.infrastructure.BracketRepository;
import com.futsal.user.presentation.dto.UserUpdateRequest;
import com.futsal.user.domain.User;
import com.futsal.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamAwardRepository teamAwardRepository;
    private final TournamentRepository tournamentRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentResultRepository resultRepository;
    private final TournamentGroupRepository groupRepository;
    private final BracketRepository bracketRepository;

    /**
     * 사용자 프로필 업데이트
     */
    @Transactional
    public Map<String, Object> updateProfile(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        // 닉네임 업데이트
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.updateNickname(request.getNickname());
        }

        // 프로필 이미지 업데이트 (S3 커스텀 이미지)
        if (request.getProfileImageUrl() != null) {
            user.updateCustomProfileImage(request.getProfileImageUrl());
        }

        // 변경사항 저장
        User updated = userRepository.save(user);

        // 응답 생성 (프로필 업데이트하는 사용자는 이미 활성 사용자이므로 roleSelected = true)
        Map<String, Object> response = new HashMap<>();
        response.put("id", updated.getId());
        response.put("kakaoId", updated.getKakaoId());
        response.put("nickname", updated.getNickname());
        response.put("profileImageUrl", updated.getProfileImageUrl());
        response.put("role", updated.getRole());
        response.put("roleSelected", true);

        return response;
    }

    /**
     * 사용자 계정 삭제
     * 연관 데이터를 FK 의존성 역순으로 삭제한 뒤 계정을 제거한다.
     */
    @Transactional
    public void deleteAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        // 1. 사용자가 팀장인 팀과 해당 팀의 모든 하위 데이터 삭제
        List<Team> captainTeams = teamRepository.findByCaptainUserId(userId);
        for (Team team : captainTeams) {
            Long teamId = team.getId();
            teamAwardRepository.deleteByTeamId(teamId);
            teamMemberRepository.deleteByTeamId(teamId);
            teamRepository.delete(team);
        }

        // 2. 사용자가 일반 멤버로 속한 팀에서 제거
        teamMemberRepository.deleteByUserId(userId);

        // 3. 사용자가 만든 대회와 해당 대회의 모든 하위 데이터 삭제
        tournamentRepository.findListByRegisteredBy(user).forEach(tournament -> {
            Long tid = tournament.getId();
            teamAwardRepository.deleteByTournamentId(tid);
            matchRepository.deleteByTournamentId(tid);
            groupRepository.deleteByTournamentId(tid);
            participantRepository.deleteByTournamentId(tid);
            resultRepository.deleteByTournamentId(tid);
            bracketRepository.deleteByTournamentId(tid);
            tournamentRepository.deleteById(tid);
        });

        // 4. 사용자가 등록한 참가 기록 삭제
        participantRepository.deleteByRegisteredBy(userId);

        userRepository.delete(user);
    }
}
