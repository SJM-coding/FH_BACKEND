package com.futsal.team.controller;

import com.futsal.team.service.TeamService;
import com.futsal.team.dto.TeamAwardResponse;
import com.futsal.team.dto.TeamCreateRequest;
import com.futsal.team.dto.TeamInviteCodeResponse;
import com.futsal.team.dto.TeamInviteRequest;
import com.futsal.team.dto.TeamMemberPositionRequest;
import com.futsal.team.dto.TeamMemberResponse;
import com.futsal.team.dto.TeamParticipationResponse;
import com.futsal.team.dto.TeamResponse;
import com.futsal.team.dto.TeamUpdateRequest;
import com.futsal.team.dto.TacticsSaveRequest;
import com.futsal.team.dto.TacticsResponse;
import jakarta.validation.Valid;
import com.futsal.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    /**
     * 팀 생성
     */
    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(
            @RequestBody TeamCreateRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        TeamResponse response = teamService.createTeam(request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * 내가 속한 팀 목록 조회
     */
    @GetMapping("/my")
    public ResponseEntity<List<TeamResponse>> getMyTeams(
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        List<TeamResponse> teams = teamService.getMyTeams(user);
        return ResponseEntity.ok(teams);
    }

    /**
     * 팀 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<TeamResponse> getTeamById(@PathVariable Long id) {
        TeamResponse team = teamService.getTeamById(id);
        return ResponseEntity.ok(team);
    }

    /**
     * 팀 멤버 목록 조회
     */
    @GetMapping("/{id}/members")
    public ResponseEntity<List<TeamMemberResponse>> getTeamMembers(@PathVariable Long id) {
        List<TeamMemberResponse> members = teamService.getTeamMembers(id);
        return ResponseEntity.ok(members);
    }

    /**
     * 팀 정보 수정
     */
    @PutMapping("/{id}")
    public ResponseEntity<TeamResponse> updateTeam(
            @PathVariable Long id,
            @RequestBody TeamUpdateRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        TeamResponse updated = teamService.updateTeam(id, request, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * 팀 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeam(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        teamService.deleteTeam(id, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * 팀 탈퇴
     */
    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leaveTeam(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        teamService.leaveTeam(id, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * 초대 코드 생성
     */
    @PostMapping("/{id}/invite-code")
    public ResponseEntity<TeamInviteCodeResponse> generateInviteCode(
            @PathVariable Long id,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        TeamInviteCodeResponse response = teamService.generateInviteCode(id, user);
        return ResponseEntity.ok(response);
    }

    /**
     * 초대 코드로 팀 가입
     */
    @PostMapping("/join")
    public ResponseEntity<TeamResponse> joinTeamByInviteCode(
            @RequestParam String code,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        TeamResponse response = teamService.joinTeamByInviteCode(code, user);
        return ResponseEntity.ok(response);
    }

    /**
     * 팀원 추방
     */
    @DeleteMapping("/{id}/members/{memberId}")
    public ResponseEntity<Void> kickMember(
            @PathVariable Long id,
            @PathVariable Long memberId,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        teamService.kickMember(id, memberId, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * 팀원 포지션 변경
     */
    @PutMapping("/{id}/members/{memberId}/position")
    public ResponseEntity<TeamMemberResponse> updateMemberPosition(
            @PathVariable Long id,
            @PathVariable Long memberId,
            @RequestBody TeamMemberPositionRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        TeamMemberResponse response = teamService.updateMemberPosition(id, memberId, request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * 전술 저장
     */
    @PostMapping("/{id}/tactics")
    public ResponseEntity<TacticsResponse> saveTactics(
            @PathVariable Long id,
            @RequestBody TacticsSaveRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        TacticsResponse response = teamService.saveTactics(id, request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * 전술 조회
     */
    @GetMapping("/{id}/tactics")
    public ResponseEntity<TacticsResponse> getTactics(@PathVariable Long id) {
        TacticsResponse response = teamService.getTactics(id);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    // ============================================
    // 팀 참가 이력
    // ============================================

    /**
     * 팀의 대회 참가 이력 조회
     */
    @GetMapping("/{id}/participations")
    public ResponseEntity<List<TeamParticipationResponse>> getTeamParticipations(@PathVariable Long id) {
        List<TeamParticipationResponse> participations = teamService.getTeamParticipations(id);
        return ResponseEntity.ok(participations);
    }

    // ============================================
    // 팀 수상 경력
    // ============================================

    /**
     * 팀 수상 경력 목록 조회
     * (수상 경력은 대회 결과 입력 시 자동 생성됨)
     */
    @GetMapping("/{id}/awards")
    public ResponseEntity<List<TeamAwardResponse>> getTeamAwards(@PathVariable Long id) {
        List<TeamAwardResponse> awards = teamService.getTeamAwards(id);
        return ResponseEntity.ok(awards);
    }
}
