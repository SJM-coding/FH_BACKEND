package com.futsal.team.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.futsal.team.domain.Team;
import com.futsal.team.domain.TeamMember;
import com.futsal.team.domain.TeamTactics;
import com.futsal.user.domain.User;
import com.futsal.team.domain.PlayerPosition;
import com.futsal.team.domain.TeamMemberRole;
import com.futsal.team.domain.TeamMemberStatus;
import com.futsal.team.dto.TeamCreateRequest;
import com.futsal.team.dto.TeamInviteCodeResponse;
import com.futsal.team.dto.TeamInviteRequest;
import com.futsal.team.dto.TeamMemberPositionRequest;
import com.futsal.team.dto.TeamMemberResponse;
import com.futsal.team.dto.TeamResponse;
import com.futsal.team.dto.TeamUpdateRequest;
import com.futsal.team.dto.TacticsPlayerPosition;
import com.futsal.team.dto.TacticsSaveRequest;
import com.futsal.team.dto.TacticsResponse;
import com.futsal.team.repository.TeamMemberRepository;
import com.futsal.team.repository.TeamRepository;
import com.futsal.team.repository.TeamTacticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamTacticsRepository teamTacticsRepository;
    private final ObjectMapper objectMapper;

    /**
     * 팀 생성
     */
    @Transactional
    public TeamResponse createTeam(TeamCreateRequest request, User captain) {
        Team team = Team.builder()
                .name(request.getName())
                .region(request.getRegion())
                .logoUrl(request.getLogoUrl())
                .captain(captain)
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
     * 내가 속한 팀 목록 조회
     */
    @Transactional(readOnly = true)
    public List<TeamResponse> getMyTeams(User user) {
        List<TeamMember> myMemberships = teamMemberRepository.findByUserAndStatus(
                user, 
                TeamMemberStatus.ACTIVE
        );
        
        return myMemberships.stream()
                .map(TeamMember::getTeam)
                .map(this::toResponse)
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
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));
        
        List<TeamMember> members = teamMemberRepository.findByTeamAndStatus(
                team, 
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
     * 팀 삭제
     */
    @Transactional
    public void deleteTeam(Long teamId, User user) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));
        
        if (!team.isCaptain(user.getId())) {
            throw new RuntimeException("팀을 삭제할 권한이 없습니다");
        }
        
        List<TeamMember> members = teamMemberRepository.findByTeam(team);
        teamMemberRepository.deleteAll(members);
        
        teamRepository.delete(team);
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
        
        if (existingTactics.isPresent()) {
            tactics = existingTactics.get();
            tactics.updateTactics(request.getFormation(), playersJson);
        } else {
            tactics = TeamTactics.builder()
                    .team(team)
                    .formation(request.getFormation())
                    .playersJson(playersJson)
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
                team.getCaptain().getId(),
                team.getCaptain().getNickname(),
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
                tactics.getCreatedAt(),
                tactics.getUpdatedAt()
        );
    }
}
