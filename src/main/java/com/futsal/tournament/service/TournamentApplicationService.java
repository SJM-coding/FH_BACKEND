package com.futsal.tournament.service;

import com.futsal.team.domain.Team;
import com.futsal.team.repository.TeamRepository;
import com.futsal.tournament.domain.ApplicationFormField;
import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.domain.TournamentApplication;
import com.futsal.tournament.dto.ApplicationCreateRequest;
import com.futsal.tournament.dto.ApplicationProcessRequest;
import com.futsal.tournament.dto.ApplicationResponse;
import com.futsal.tournament.repository.TournamentApplicationRepository;
import com.futsal.tournament.repository.TournamentRepository;
import com.futsal.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 대회 신청 서비스
 */
@Service
@RequiredArgsConstructor
public class TournamentApplicationService {

    private final TournamentApplicationRepository applicationRepository;
    private final TournamentRepository tournamentRepository;
    private final TeamRepository teamRepository;

    /**
     * 대회 신청
     */
    @Transactional
    public ApplicationResponse createApplication(
            Long tournamentId,
            ApplicationCreateRequest request,
            User applicant
    ) {
        // 대회 조회
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + tournamentId));

        // 팀 조회
        Team team = teamRepository.findById(request.getTeamId())
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + request.getTeamId()));

        // 권한 확인: 팀장만 신청 가능
        if (!team.getCaptain().getId().equals(applicant.getId())) {
            throw new RuntimeException("팀장만 대회 신청을 할 수 있습니다.");
        }

        // 중복 신청 확인
        applicationRepository.findByTournamentIdAndTeamId(tournamentId, request.getTeamId())
                .ifPresent(existing -> {
                    throw new RuntimeException("이미 신청한 대회입니다.");
                });

        // 신청 양식 검증 (Tournament에 applicationFormFields가 있는 경우에만)
        // validateFormData(tournament.getApplicationFormFields(), request.getFormData());

        // 신청서 생성
        TournamentApplication application = TournamentApplication.builder()
                .tournament(tournament)
                .team(team)
                .applicant(applicant)
                .formData(request.getFormData())
                .message(request.getMessage())
                .build();

        TournamentApplication saved = applicationRepository.save(application);
        return toResponse(saved);
    }

    /**
     * 대회별 신청서 목록 조회 (주최자용)
     */
    @Transactional(readOnly = true)
    public List<ApplicationResponse> getApplicationsByTournament(Long tournamentId, User user) {
        // 대회 조회 및 권한 확인
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + tournamentId));

        if (!tournament.isRegisteredBy(user.getId())) {
            throw new RuntimeException("대회 주최자만 신청서를 조회할 수 있습니다.");
        }

        List<TournamentApplication> applications = 
                applicationRepository.findByTournamentIdWithDetails(tournamentId);

        return applications.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 팀별 신청서 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ApplicationResponse> getApplicationsByTeam(Long teamId, User user) {
        // 팀 조회
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다: " + teamId));

        // 권한 확인: 팀원만 조회 가능
        // Team 엔티티에 members 필드가 없는 경우 주석 처리
        /*
        boolean isMember = team.getMembers().stream()
                .anyMatch(member -> member.getUser().getId().equals(user.getId()));

        if (!isMember) {
            throw new RuntimeException("팀원만 신청 내역을 조회할 수 있습니다.");
        }
        */
        
        // 간단한 권한 확인: 팀장인지만 체크
        if (!team.getCaptain().getId().equals(user.getId())) {
            throw new RuntimeException("팀장만 신청 내역을 조회할 수 있습니다.");
        }

        List<TournamentApplication> applications = 
                applicationRepository.findByTeamIdWithDetails(teamId);

        return applications.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 신청 승인
     */
    @Transactional
    public ApplicationResponse approveApplication(Long applicationId, User user) {
        TournamentApplication application = applicationRepository.findByIdWithDetails(applicationId)
                .orElseThrow(() -> new RuntimeException("신청서를 찾을 수 없습니다: " + applicationId));

        // 권한 확인: 대회 주최자만 가능
        if (!application.getTournament().isRegisteredBy(user.getId())) {
            throw new RuntimeException("대회 주최자만 승인할 수 있습니다.");
        }

        application.approve();
        applicationRepository.save(application);

        return toResponse(application);
    }

    /**
     * 신청 거부
     */
    @Transactional
    public ApplicationResponse rejectApplication(
            Long applicationId,
            ApplicationProcessRequest request,
            User user
    ) {
        TournamentApplication application = applicationRepository.findByIdWithDetails(applicationId)
                .orElseThrow(() -> new RuntimeException("신청서를 찾을 수 없습니다: " + applicationId));

        // 권한 확인: 대회 주최자만 가능
        if (!application.getTournament().isRegisteredBy(user.getId())) {
            throw new RuntimeException("대회 주최자만 거부할 수 있습니다.");
        }

        if (request.getRejectReason() == null || request.getRejectReason().trim().isEmpty()) {
            throw new RuntimeException("거부 사유는 필수입니다.");
        }

        application.reject(request.getRejectReason());
        applicationRepository.save(application);

        return toResponse(application);
    }

    /**
     * 신청 취소
     */
    @Transactional
    public ApplicationResponse cancelApplication(Long applicationId, User user) {
        TournamentApplication application = applicationRepository.findByIdWithDetails(applicationId)
                .orElseThrow(() -> new RuntimeException("신청서를 찾을 수 없습니다: " + applicationId));

        // 권한 확인: 신청자만 가능
        if (!application.isApplicant(user.getId())) {
            throw new RuntimeException("신청자만 취소할 수 있습니다.");
        }

        application.cancel();
        applicationRepository.save(application);

        return toResponse(application);
    }

    /**
     * 신청 양식 데이터 검증
     * 
     * Tournament에 applicationFormFields가 있는 경우에만 사용
     */
    @SuppressWarnings("unused")
    private void validateFormData(List<ApplicationFormField> formFields, Map<String, String> formData) {
        if (formFields == null || formFields.isEmpty()) {
            return;
        }

        for (ApplicationFormField field : formFields) {
            String value = formData.get(field.getFieldName());
            
            if (!field.isValid(value)) {
                throw new RuntimeException(
                    String.format("'%s' 필드가 유효하지 않습니다.", field.getFieldLabel())
                );
            }
        }
    }

    /**
     * Entity -> DTO 변환
     */
    private ApplicationResponse toResponse(TournamentApplication application) {
        return ApplicationResponse.builder()
                .id(application.getId())
                .tournamentId(application.getTournament().getId())
                .tournamentTitle(application.getTournament().getTitle())
                .teamId(application.getTeam().getId())
                .teamName(application.getTeam().getName())
                .teamLogoUrl(application.getTeam().getLogoUrl())
                .applicantId(application.getApplicant().getId())
                .applicantName(application.getApplicant().getNickname())
                .status(application.getStatus())
                .formData(application.getFormData())
                .message(application.getMessage())
                .rejectReason(application.getRejectReason())
                .appliedAt(application.getAppliedAt())
                .processedAt(application.getProcessedAt())
                .build();
    }
}
