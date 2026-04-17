package com.futsal.team.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.futsal.team.domain.Team;
import com.futsal.team.domain.TeamAward;
import com.futsal.team.domain.TeamMember;
import com.futsal.team.domain.TeamTactics;
import com.futsal.tournament.domain.Tournament;
import com.futsal.user.domain.User;
import com.futsal.team.domain.PlayerPosition;
import com.futsal.team.domain.TeamMemberRole;
import com.futsal.team.domain.TeamMemberStatus;
import com.futsal.team.presentation.dto.TeamAwardResponse;
import com.futsal.team.presentation.dto.TeamCreateRequest;
import com.futsal.team.presentation.dto.TeamInviteCodeResponse;
import com.futsal.team.presentation.dto.TeamInviteRequest;
import com.futsal.team.presentation.dto.TeamMemberPositionRequest;
import com.futsal.team.presentation.dto.TeamMemberResponse;
import com.futsal.team.presentation.dto.TeamParticipationResponse;
import com.futsal.team.presentation.dto.TeamResponse;
import com.futsal.team.presentation.dto.TeamUpdateRequest;
import com.futsal.team.presentation.dto.TacticsPlayerPosition;
import com.futsal.team.presentation.dto.TacticsSaveRequest;
import com.futsal.team.presentation.dto.TacticsResponse;
import com.futsal.team.infrastructure.TeamAwardRepository;
import com.futsal.team.infrastructure.TeamMemberRepository;
import com.futsal.team.infrastructure.TeamRepository;
import com.futsal.team.infrastructure.TeamTacticsRepository;
import com.futsal.tournament.domain.TournamentParticipant;
import com.futsal.tournament.infrastructure.TournamentParticipantRepository;
import com.futsal.tournament.infrastructure.TournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.futsal.user.infrastructure.UserRepository;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamTacticsRepository teamTacticsRepository;
    private final TeamAwardRepository teamAwardRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentRepository tournamentRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    /**
     * 팀 생성
     */
    @Transactional
    public TeamResponse createTeam(TeamCreateRequest request, User captain) {
        Team team = Team.builder()
                .name(request.getName())
                .region(request.getRegion())
                .logoUrl(request.getLogoUrl())
                .captainUserId(captain.getId())
                .build();
        
        Team savedTeam = teamRepository.save(team);
        
        TeamMember captainMember = TeamMember.builder()
                .team(savedTeam)
                .user(captain)
                .role(TeamMemberRole.CAPTAIN)
                .status(TeamMemberStatus.ACTIVE)
                .position(PlayerPosition.NONE)
                .build();
        
        teamMemberRepository.save(captainMember);
        
        return toResponse(savedTeam);
    }

    /**
     * 내가 속한 팀 목록 조회 (N+1 방지)
     */
    @Transactional(readOnly = true)
    public List<TeamResponse> getMyTeams(User user) {
        // 1. Team을 JOIN FETCH로 한 번에 조회
        List<TeamMember> myMemberships = teamMemberRepository.findByUserAndStatusWithTeam(
                user,
                TeamMemberStatus.ACTIVE
        );

        if (myMemberships.isEmpty()) {
            return List.of();
        }

        // 2. 팀 ID 목록 추출
        List<Long> teamIds = myMemberships.stream()
                .map(tm -> tm.getTeam().getId())
                .collect(Collectors.toList());

        // 3. 모든 팀의 memberCount를 한 번에 조회
        Map<Long, Long> memberCountMap = teamMemberRepository
                .countByTeamIdsAndStatus(teamIds, TeamMemberStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        // 4. DTO 변환
        Map<Long, String> captainNameMap = buildCaptainNameMap(
                myMemberships.stream()
                        .map(tm -> tm.getTeam().getCaptainUserId())
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList())
        );

        return myMemberships.stream()
                .map(tm -> {
                    Team team = tm.getTeam();
                    int memberCount = memberCountMap.getOrDefault(team.getId(), 0L).intValue();
                    return new TeamResponse(
                            team.getId(),
                            team.getName(),
                            team.getRegion(),
                            team.getLogoUrl(),
                            team.getCaptainUserId(),
                            captainNameMap.get(team.getCaptainUserId()),
                            memberCount,
                            team.getCreatedAt()
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * 팀 상세 조회
     */
    @Transactional(readOnly = true)
    public TeamResponse getTeamById(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));
        return toResponse(team);
    }

    /**
     * 팀 멤버 목록 조회
     */
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> getTeamMembers(Long teamId) {
        if (!teamRepository.existsById(teamId)) {
            throw new RuntimeException("팀을 찾을 수 없습니다: " + teamId);
        }
        
        List<TeamMember> members = teamMemberRepository.findByTeamIdAndStatusWithUser(
                teamId,
                TeamMemberStatus.ACTIVE
        );
        
        return members.stream()
                .map(this::toMemberResponse)
                .collect(Collectors.toList());
    }

    /**
     * 팀 정보 수정
     */
    @Transactional
    public TeamResponse updateTeam(Long teamId, TeamUpdateRequest request, User user) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));
        
        if (!team.isCaptain(user.getId())) {
            throw new RuntimeException("팀 정보를 수정할 권한이 없습니다");
        }
        
        team.updateTeam(request.getName(), request.getRegion(), request.getLogoUrl());
        
        Team updated = teamRepository.save(team);
        return toResponse(updated);
    }

    /**
     * 팀 삭제 (소프트 딜리트)
     *  TeamAward, UserAward 등 수상 이력은 그대로 보존된다.
     */
    @Transactional
    public void deleteTeam(Long teamId, User user) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));

        if (!team.isCaptain(user.getId())) {
            throw new RuntimeException("팀을 삭제할 권한이 없습니다");
        }

        team.delete();
        teamRepository.save(team);
    }

    /**
     * 팀 탈퇴
     */
    @Transactional
    public void leaveTeam(Long teamId, User user) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));
        
        if (team.isCaptain(user.getId())) {
            throw new RuntimeException("팀장은 팀을 탈퇴할 수 없습니다. 팀을 삭제하거나 팀장을 위임하세요.");
        }
        
        TeamMember member = teamMemberRepository.findByTeamAndUser(team, user)
                .orElseThrow(() -> new RuntimeException("팀원이 아닙니다"));
        
        teamMemberRepository.delete(member);
    }

    /**
     * 초대 코드 생성
     */
    @Transactional(readOnly = true)
    public TeamInviteCodeResponse generateInviteCode(Long teamId, User user) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));
        
        if (!team.isCaptain(user.getId())) {
            throw new RuntimeException("초대 코드를 생성할 권한이 없습니다");
        }
        
        String inviteCode = Base64.getEncoder().encodeToString(
            String.format("TEAM_%d_%s", teamId, UUID.randomUUID().toString().substring(0, 8)).getBytes()
        );
        
        return new TeamInviteCodeResponse(inviteCode, teamId, team.getName());
    }

    /**
     * 초대 코드로 팀 가입
     */
    @Transactional
    public TeamResponse joinTeamByInviteCode(String inviteCode, User user) {
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(inviteCode));
        } catch (Exception e) {
            throw new RuntimeException("유효하지 않은 초대 코드입니다");
        }
        
        if (!decoded.startsWith("TEAM_")) {
            throw new RuntimeException("유효하지 않은 초대 코드입니다");
        }
        
        String[] parts = decoded.split("_");
        if (parts.length < 2) {
            throw new RuntimeException("유효하지 않은 초대 코드입니다");
        }
        
        Long teamId;
        try {
            teamId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new RuntimeException("유효하지 않은 초대 코드입니다");
        }
        
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다"));

        // 대회 참가 중인 팀은 신규 멤버 추가 불가 — 당시 팀 구성 보존
        if (participantRepository.existsByTeamIdAndStatus(
                teamId, TournamentParticipant.ParticipantStatus.CONFIRMED)) {
            throw new IllegalStateException("대회 참가 중인 팀에는 새로운 멤버가 합류할 수 없습니다.");
        }

        Optional<TeamMember> existingMember = teamMemberRepository.findByTeamAndUser(team, user);
        if (existingMember.isPresent()) {
            if (existingMember.get().getStatus() == TeamMemberStatus.ACTIVE) {
                throw new RuntimeException("이미 팀에 가입되어 있습니다");
            }
        }
        
        TeamMember newMember = TeamMember.builder()
                .team(team)
                .user(user)
                .role(TeamMemberRole.MEMBER)
                .status(TeamMemberStatus.ACTIVE)
                .position(PlayerPosition.NONE)
                .build();
        
        teamMemberRepository.save(newMember);
        
        return toResponse(team);
    }

    /**
     * 팀원 추방
     */
    @Transactional
    public void kickMember(Long teamId, Long memberId, User user) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));
        
        if (!team.isCaptain(user.getId())) {
            throw new RuntimeException("팀원을 추방할 권한이 없습니다");
        }
        
        TeamMember member = teamMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("팀원을 찾을 수 없습니다"));
        
        if (member.getRole() == TeamMemberRole.CAPTAIN) {
            throw new RuntimeException("팀장은 추방할 수 없습니다");
        }
        
        teamMemberRepository.delete(member);
    }

    /**
     * 팀원 포지션 변경
     */
    @Transactional
    public TeamMemberResponse updateMemberPosition(Long teamId, Long memberId, TeamMemberPositionRequest request, User user) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));
        
        if (!team.isCaptain(user.getId())) {
            throw new RuntimeException("포지션을 변경할 권한이 없습니다");
        }
        
        TeamMember member = teamMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("팀원을 찾을 수 없습니다"));
        
        member.updatePosition(request.getPosition());
        TeamMember updated = teamMemberRepository.save(member);
        
        return toMemberResponse(updated);
    }

    /**
     * 전술 저장
     */
    @Transactional
    public TacticsResponse saveTactics(Long teamId, TacticsSaveRequest request, User user) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));
        
        if (!team.isCaptain(user.getId())) {
            throw new RuntimeException("전술을 저장할 권한이 없습니다");
        }
        
        String playersJson;
        try {
            playersJson = objectMapper.writeValueAsString(request.getPlayers());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("전술 데이터 저장에 실패했습니다");
        }
        
        Optional<TeamTactics> existingTactics = teamTacticsRepository.findByTeam(team);
        TeamTactics tactics;
        String framesJson = request.getFramesJson(); // 프레임 JSON (이미 문자열)

        if (existingTactics.isPresent()) {
            tactics = existingTactics.get();
            tactics.updateTactics(request.getFormation(), playersJson, framesJson);
        } else {
            tactics = TeamTactics.builder()
                    .team(team)
                    .formation(request.getFormation())
                    .playersJson(playersJson)
                    .framesJson(framesJson)
                    .build();
        }
        
        TeamTactics saved = teamTacticsRepository.save(tactics);
        return toTacticsResponse(saved);
    }

    /**
     * 전술 조회
     */
    @Transactional(readOnly = true)
    public TacticsResponse getTactics(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));
        
        Optional<TeamTactics> tactics = teamTacticsRepository.findByTeam(team);
        
        return tactics.map(this::toTacticsResponse).orElse(null);
    }

    // ============================================
    // 팀 참가 이력 조회
    // ============================================

    /**
     * 팀의 대회 참가 이력 조회
     */
    @Transactional(readOnly = true)
    public List<TeamParticipationResponse> getTeamParticipations(Long teamId) {
        // 팀 존재 확인
        teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));

        List<TournamentParticipant> participations =
                participantRepository.findConfirmedByTeamId(teamId);

        List<Long> tournamentIds = participations.stream()
                .map(TournamentParticipant::getTournamentId)
                .collect(Collectors.toList());
        Map<Long, Tournament> tournamentMap = tournamentRepository.findAllById(tournamentIds)
                .stream()
                .collect(Collectors.toMap(Tournament::getId, t -> t));

        return participations.stream()
                .filter(p -> tournamentMap.containsKey(p.getTournamentId()))
                .map(p -> TeamParticipationResponse.from(p, tournamentMap.get(p.getTournamentId())))
                .collect(Collectors.toList());
    }

    // ============================================
    // 팀 수상 경력 관리
    // ============================================

    /**
     * 팀 수상 경력 목록 조회
     * (수상 경력은 대회 결과 입력 시 자동 생성됨)
     */
    @Transactional(readOnly = true)
    public List<TeamAwardResponse> getTeamAwards(Long teamId) {
        // 팀 존재 확인
        teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));

        List<TeamAward> awards = teamAwardRepository.findByTeamIdOrderByAwardDateDesc(teamId);
        return awards.stream()
                .map(TeamAwardResponse::from)
                .collect(Collectors.toList());
    }

    // ============================================
    // 변환 메서드
    // ============================================

    private TeamResponse toResponse(Team team) {
        int memberCount = teamMemberRepository.countByTeamAndStatus(
                team, 
                TeamMemberStatus.ACTIVE
        );
        
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getRegion(),
                team.getLogoUrl(),
                team.getCaptainUserId(),
                getCaptainName(team.getCaptainUserId()),
                memberCount,
                team.getCreatedAt()
        );
    }

    private TeamMemberResponse toMemberResponse(TeamMember member) {
        return new TeamMemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getNickname(),
                member.getUser().getProfileImageUrl(),
                member.getRole(),
                member.getStatus(),
                member.getPosition(),
                member.getJoinedAt()
        );
    }

    private String getCaptainName(Long captainUserId) {
        if (captainUserId == null) {
            return null;
        }
        return userRepository.findById(captainUserId)
                .map(User::getNickname)
                .orElse(null);
    }

    private Map<Long, String> buildCaptainNameMap(List<Long> captainUserIds) {
        if (captainUserIds == null || captainUserIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        userRepository.findAllById(captainUserIds)
                .forEach(user -> map.put(user.getId(), user.getNickname()));
        return map;
    }

    private TacticsResponse toTacticsResponse(TeamTactics tactics) {
        List<TacticsPlayerPosition> players;
        try {
            players = objectMapper.readValue(
                tactics.getPlayersJson(),
                new TypeReference<List<TacticsPlayerPosition>>() {}
            );
        } catch (JsonProcessingException e) {
            players = List.of();
        }

        return new TacticsResponse(
                tactics.getId(),
                tactics.getTeam().getId(),
                tactics.getFormation(),
                players,
                tactics.getFramesJson(), // 프레임 JSON
                tactics.getCreatedAt(),
                tactics.getUpdatedAt()
        );
    }
}
