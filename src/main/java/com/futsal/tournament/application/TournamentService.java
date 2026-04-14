package com.futsal.tournament.application;

import com.futsal.tournament.domain.Bracket;
import com.futsal.tournament.domain.Gender;
import com.futsal.tournament.domain.PlayerType;
import com.futsal.tournament.domain.ShareCode;
import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.domain.TournamentParticipant;
import com.futsal.tournament.domain.TournamentType;
import com.futsal.tournament.presentation.dto.TournamentCreateRequest;
import com.futsal.tournament.presentation.dto.TournamentListResponse;
import com.futsal.tournament.presentation.dto.TournamentPageResponse;
import com.futsal.tournament.presentation.dto.TournamentResponse;
import com.futsal.tournament.presentation.dto.TournamentUpdateRequest;
import com.futsal.tournament.infrastructure.BracketRepository;
import com.futsal.tournament.infrastructure.TournamentGroupRepository;
import com.futsal.tournament.infrastructure.TournamentMatchRepository;
import com.futsal.tournament.infrastructure.TournamentParticipantRepository;
import com.futsal.tournament.infrastructure.TournamentRepository;
import com.futsal.tournament.infrastructure.TournamentResultRepository;
import com.futsal.team.infrastructure.TeamAwardRepository;
import com.futsal.user.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TournamentService {

    private static final long NEW_TOURNAMENT_HOURS = 24;
    private static final int REMAINING_TEAMS_ALERT_THRESHOLD = 4;

    private final TournamentRepository tournamentRepository;
    private final BracketRepository bracketRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentResultRepository resultRepository;
    private final TournamentGroupRepository groupRepository;
    private final TeamAwardRepository teamAwardRepository;
    private final TournamentViewCountService tournamentViewCountService;
    private final String defaultPosterUrl;

    public TournamentService(TournamentRepository tournamentRepository,
                             BracketRepository bracketRepository,
                             TournamentMatchRepository matchRepository,
                             TournamentParticipantRepository participantRepository,
                             TournamentResultRepository resultRepository,
                             TournamentGroupRepository groupRepository,
                             TeamAwardRepository teamAwardRepository,
                             TournamentViewCountService tournamentViewCountService,
                             @Value("${app.poster.default-url:}") String defaultPosterUrl) {
        this.tournamentRepository = tournamentRepository;
        this.bracketRepository = bracketRepository;
        this.matchRepository = matchRepository;
        this.participantRepository = participantRepository;
        this.resultRepository = resultRepository;
        this.groupRepository = groupRepository;
        this.teamAwardRepository = teamAwardRepository;
        this.tournamentViewCountService = tournamentViewCountService;
        this.defaultPosterUrl = defaultPosterUrl;
    }

    /**
     * 대회 등록
     */
    @Transactional
    public TournamentResponse createTournament(TournamentCreateRequest request, User registeredBy) {
        // 중복 대회 체크
        if (tournamentRepository.existsByTitleAndTournamentDateAndRegisteredBy(
                request.getTitle(),
                request.getTournamentDate(),
                registeredBy)) {
            throw new IllegalArgumentException("동일한 제목과 날짜의 대회가 이미 등록되어 있습니다.");
        }

        // 외부 대회일 경우 allowJoin = false 강제
        Boolean isExternal = request.getIsExternal() != null ? request.getIsExternal() : false;
        Boolean allowJoin = isExternal ? false : true;

        // 참가 코드 생성 (내부 대회만) — ShareCode VO가 고유성 검증 담당
        ShareCode shareCode = null;
        if (!isExternal) {
            shareCode = ShareCode.generateParticipantCode(
                code -> !tournamentRepository.existsByParticipantCode(code));
        }

        // 외부 대회: EXTERNAL 타입, maxTeams=0 / 내부 대회: 요청값 또는 기본값
        TournamentType tournamentType;
        Integer maxTeams;
        if (isExternal) {
            tournamentType = TournamentType.EXTERNAL;
            maxTeams = 0;
        } else {
            tournamentType = request.getTournamentType() != null
                    ? request.getTournamentType()
                    : TournamentType.SINGLE_ELIMINATION;
            maxTeams = request.getMaxTeams() != null ? request.getMaxTeams() : 16;
        }

        Tournament tournament = Tournament.builder()
                .title(request.getTitle())
                .tournamentDate(request.getTournamentDate())
                .location(request.getLocation())
                .playerType(request.getPlayerType())
                .gender(request.getGender())
                .description(request.getDescription())
                .viewCount(0)
                .originalLink(request.getOriginalLink() != null ? request.getOriginalLink() : "")
                .tournamentType(tournamentType)
                .maxTeams(maxTeams)
                .groupCount(request.getGroupCount())
                .teamsPerGroup(request.getTeamsPerGroup())
                .advanceCount(request.getAdvanceCount() != null ? request.getAdvanceCount() : 2)
                .swissRounds(request.getSwissRounds())
                .posterUrls(normalizePosterUrls(request.getPosterUrls()))
                .isExternal(isExternal)
                .externalUrl(request.getExternalUrl())
                .allowJoin(allowJoin)
                .shareCode(shareCode)
                .registeredBy(registeredBy)
                .build();

        Tournament saved = tournamentRepository.save(tournament);

        // Bracket Aggregate 함께 생성 (Tournament와 라이프사이클 공유)
        Bracket bracket = Bracket.createDefault(saved.getId());

        // 조별리그 + 분리 토너먼트 설정
        boolean isSplit = "SPLIT".equalsIgnoreCase(request.getKnockoutType());
        if (isSplit && request.getTeamsPerGroup() != null && request.getTeamsPerGroup() > 0) {
            int splitCount = request.getTeamsPerGroup() / 2;
            bracket.configureSplit(splitCount);
        }

        bracketRepository.save(bracket);

        return toResponse(saved);
    }

    /**
     * Phase 2-2: 대회 수정 (등록자만 가능)
     */
    @Transactional
    public TournamentResponse updateTournament(Long id, TournamentUpdateRequest request, User user) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + id));

        // 권한 확인
        if (!tournament.isRegisteredBy(user.getId())) {
            throw new RuntimeException("대회를 수정할 권한이 없습니다");
        }

        // 대진표 구조 변경 요청 여부 확인
        boolean isStructureChange = request.getMaxTeams() != null
            || request.getGroupCount() != null
            || request.getTeamsPerGroup() != null
            || request.getSwissRounds() != null
            || request.getTournamentType() != null;

        if (isStructureChange) {
            Bracket bracket = bracketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                    "대진표 정보를 찾을 수 없습니다: " + id));
            if (bracket.isGenerated()) {
                throw new IllegalStateException(
                    "대진표가 이미 생성된 대회의 구조(팀 수, 조 구성, 대회 방식)는 변경할 수 없습니다.");
            }
        }

        // maxTeams 감소 시 현재 확정된 참가 팀 수 초과 여부 체크
        if (request.getMaxTeams() != null) {
            long confirmedCount = participantRepository.countByTournamentIdAndStatus(
                id, TournamentParticipant.ParticipantStatus.CONFIRMED);
            if (request.getMaxTeams() < confirmedCount) {
                throw new IllegalArgumentException(
                    "최대 참가 팀 수는 현재 확정된 참가 팀 수(" + confirmedCount
                        + "팀)보다 작을 수 없습니다.");
            }
        }

        // 수정
        if (request.getTitle() != null) tournament.changeTitle(request.getTitle());
        if (request.getTournamentDate() != null) tournament.setTournamentDate(request.getTournamentDate());
        if (request.getLocation() != null) tournament.setLocation(request.getLocation());
        if (request.getGender() != null) tournament.setGender(request.getGender());
        if (request.getPlayerType() != null) tournament.setPlayerType(request.getPlayerType());
        if (request.getDescription() != null) tournament.setDescription(request.getDescription());
        if (request.getOriginalLink() != null) tournament.setOriginalLink(request.getOriginalLink());
        if (request.getPosterUrls() != null) tournament.setPosterUrls(normalizePosterUrls(request.getPosterUrls()));
        if (request.getRecruitmentStatus() != null) tournament.setRecruitmentStatus(request.getRecruitmentStatus());
        if (request.getTournamentType() != null) tournament.setTournamentType(request.getTournamentType());
        if (request.getMaxTeams() != null) tournament.setMaxTeams(request.getMaxTeams());
        if (request.getGroupCount() != null) tournament.setGroupCount(request.getGroupCount());
        if (request.getTeamsPerGroup() != null) tournament.setTeamsPerGroup(request.getTeamsPerGroup());
        if (request.getAdvanceCount() != null) tournament.setAdvanceCount(request.getAdvanceCount());
        if (request.getSwissRounds() != null) tournament.setSwissRounds(request.getSwissRounds());
        if (request.getIsExternal() != null) {
            tournament.setIsExternal(request.getIsExternal());
            if (request.getIsExternal()) {
                // 외부 대회로 변경 시: EXTERNAL 타입, maxTeams=0, allowJoin=false
                tournament.setTournamentType(TournamentType.EXTERNAL);
                tournament.setMaxTeams(0);
                tournament.setAllowJoin(false);
            }
        }
        if (request.getExternalUrl() != null) tournament.setExternalUrl(request.getExternalUrl());
        
        Tournament saved = tournamentRepository.save(tournament);
        return toResponse(saved);
    }

    /**
     * 내가 등록한 대회 목록
     */
    @Transactional(readOnly = true)
    public List<TournamentListResponse> getMyTournaments(User user) {
        List<Tournament> tournaments = tournamentRepository.findListByRegisteredBy(user);
        List<TournamentListResponse> responses = tournaments.stream()
            .map(this::toListResponse)
            .collect(Collectors.toList());
        populatePosterUrls(responses);
        populateParticipantSummaries(responses, tournaments);
        return responses;
    }

    /**
     * Tournament 엔티티 → TournamentListResponse 변환
     * posterUrl은 별도로 populatePosterUrls 에서 채워진다.
     */
    private TournamentListResponse toListResponse(Tournament t) {
        com.futsal.user.domain.User owner = t.getRegisteredBy();
        boolean isNew = isNewTournament(t);
        return new TournamentListResponse(
            t.getId(),
            t.getTitle(),
            t.getTournamentDate(),
            t.getLocation(),
            t.getRecruitmentStatus(),
            "",
            owner != null ? owner.getNickname() : null,
            owner != null ? owner.getProfileImageUrl() : null,
            t.getGender(),
            t.getPlayerType(),
            t.getIsExternal(),
            owner != null ? owner.getVerificationStatus() : null,
            isNew
        );
    }

    private List<String> normalizePosterUrls(List<String> posterUrls) {
        if (posterUrls == null || posterUrls.isEmpty()) {
            if (defaultPosterUrl != null && !defaultPosterUrl.isBlank()) {
                List<String> defaultList = new ArrayList<>();
                defaultList.add(defaultPosterUrl);
                return defaultList;
            }
            return new ArrayList<>();
        }
        return posterUrls;
    }

    private TournamentResponse toResponse(Tournament tournament) {
        return toResponse(tournament, tournament.getViewCount());
    }

    private LocalDateTime getNewTournamentThreshold() {
        return LocalDateTime.now().minusHours(NEW_TOURNAMENT_HOURS);
    }

    private boolean isNewTournament(Tournament tournament) {
        return tournament.getCreatedAt() != null
            && tournament.getCreatedAt().isAfter(getNewTournamentThreshold());
    }

    private TournamentResponse toResponse(Tournament tournament, int viewCount) {
        Bracket bracket = bracketRepository.findByTournamentId(tournament.getId())
            .orElseGet(() -> Bracket.createDefault(tournament.getId()));

        TournamentResponse response = new TournamentResponse(
                tournament.getId(),
                tournament.getTitle(),
                tournament.getTournamentDate(),
                tournament.getLocation(),
                tournament.getPlayerType(),
                tournament.getGender(),
                tournament.getDescription(),
                viewCount,
                tournament.getOriginalLink(),
                tournament.getTournamentType().name(),
                tournament.getMaxTeams(),
                tournament.getGroupCount(),
                tournament.getTeamsPerGroup(),
                tournament.getAdvanceCount(),
                tournament.getSwissRounds(),
                bracket.isGenerated(),
                bracket.getType().name(),
                bracket.getImageUrls(),
                tournament.getPosterUrls() != null ? tournament.getPosterUrls() : new ArrayList<>(),
                tournament.getRecruitmentStatus(),
                tournament.getRegisteredBy() != null ? tournament.getRegisteredBy().getId() : null,
                tournament.getRegisteredBy() != null ? tournament.getRegisteredBy().getNickname() : null,
                tournament.getRegisteredBy() != null ? tournament.getRegisteredBy().getRole().name() : null,
                tournament.getRegisteredBy() != null ? tournament.getRegisteredBy().isVerified() : false,
                tournament.getCreatedAt(),
                tournament.getIsExternal(),
                tournament.getExternalUrl(),
                null,
                null,
                null,
                tournament.getParticipantCode(),
                tournament.getStaffCode()
        );
        enrichParticipantSummary(response, tournament, getConfirmedParticipantCount(tournament.getId()));
        return response;
    }

    /**
     * String을 Gender enum으로 변환
     */
    private Gender parseGender(String gender) {
        if (gender == null || gender.isBlank()) return null;
        try {
            return Gender.valueOf(gender.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * String을 PlayerType enum으로 변환
     */
    private PlayerType parsePlayerType(String playerType) {
        if (playerType == null || playerType.isBlank()) return null;
        try {
            return PlayerType.valueOf(playerType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 대회 목록 페이지네이션 조회 (서버 정렬 + 필터)
     * 정렬: 진행중(오늘) > 모집중 > 예정 > 종료, 같은 상태면 날짜순
     */
    @Transactional(readOnly = true)
    public TournamentPageResponse getTournaments(
        String genderStr, String playerTypeStr, String recruitmentStatus,
        int page, int size
    ) {
        Gender gender = parseGender(genderStr);
        PlayerType playerType = parsePlayerType(playerTypeStr);
        String status = (recruitmentStatus != null && !recruitmentStatus.isBlank())
            ? recruitmentStatus : null;

        Page<Tournament> result = tournamentRepository.findPaged(
            gender, playerType, status, getNewTournamentThreshold(), PageRequest.of(page, size)
        );

        List<TournamentListResponse> content = result.getContent().stream()
            .map(this::toListResponse)
            .collect(Collectors.toList());
        populatePosterUrls(content);
        populateParticipantSummaries(content, result.getContent());

        return new TournamentPageResponse(content, result.hasNext(), result.getTotalElements());
    }

    /**
     * 키워드 검색 페이지네이션
     */
    @Transactional(readOnly = true)
    public TournamentPageResponse searchTournaments(String keyword, int page, int size) {
        Page<Tournament> result = tournamentRepository.findPagedByKeyword(
            keyword, getNewTournamentThreshold(), PageRequest.of(page, size)
        );

        List<TournamentListResponse> content = result.getContent().stream()
            .map(this::toListResponse)
            .collect(Collectors.toList());
        populatePosterUrls(content);
        populateParticipantSummaries(content, result.getContent());

        return new TournamentPageResponse(content, result.hasNext(), result.getTotalElements());
    }

    /**
     * 사이트맵용 전체 목록
     */
    @Transactional(readOnly = true)
    public List<TournamentListResponse> getAllTournaments() {
        List<Tournament> tournaments = tournamentRepository.findListAll();
        List<TournamentListResponse> responses = tournaments.stream()
            .map(this::toListResponse)
            .collect(Collectors.toList());
        populateParticipantSummaries(responses, tournaments);
        return responses;
    }

    /**
     * 단일 대회 조회 (조회수 증가)
     */
    @Transactional
    public TournamentResponse getTournamentById(Long id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + id));

        int visibleViewCount = tournamentViewCountService.recordViewAndGetVisibleCount(tournament);
        return toResponse(tournament, visibleViewCount);
    }

    /**
     * 참가 코드로 대회 조회 (참가 신청용)
     */
    @Transactional(readOnly = true)
    public TournamentResponse getTournamentByParticipantCode(String participantCode) {
        Tournament tournament = tournamentRepository.findByParticipantCode(participantCode)
                .orElseThrow(() -> new RuntimeException("유효하지 않은 참가코드입니다: " + participantCode));
        return toResponse(tournament);
    }

    /**
     * 운영진 코드로 대회 조회 (점수 입력용)
     */
    @Transactional(readOnly = true)
    public TournamentResponse getTournamentByStaffCode(String staffCode) {
        Tournament tournament = tournamentRepository.findByStaffCode(staffCode)
                .orElseThrow(() -> new RuntimeException("유효하지 않은 운영진코드입니다: " + staffCode));
        return toResponse(tournament);
    }

    /**
     * 대회 삭제 (등록자만 가능)
     */
    @Transactional
    public void deleteTournament(Long id, User user) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + id));

        // 권한 확인
        if (!tournament.isRegisteredBy(user.getId())) {
            throw new RuntimeException("대회를 삭제할 권한이 없습니다");
        }

        // Team BC 먼저 (FK 의존성 없음)
        teamAwardRepository.deleteByTournamentId(id);
        // Tournament BC 자식 데이터 삭제
        matchRepository.deleteByTournamentId(id);
        groupRepository.deleteByTournamentId(id);
        participantRepository.deleteByTournamentId(id);
        resultRepository.deleteByTournamentId(id);
        bracketRepository.deleteByTournamentId(id);
        tournamentRepository.deleteById(id);
    }

    @SuppressWarnings("unchecked")
    private void populatePosterUrls(List<TournamentListResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }

        List<Long> ids = responses.stream()
                .map(TournamentListResponse::getId)
                .collect(Collectors.toList());

        List<Object[]> results = tournamentRepository.findPosterUrlsByIds(ids);

        java.util.Map<Long, String> firstPosterByTournamentId = new java.util.HashMap<>();
        for (Object[] row : results) {
            Long id = (Long) row[0];
            List<String> posterUrls = (List<String>) row[1];
            if (posterUrls != null && !posterUrls.isEmpty()) {
                firstPosterByTournamentId.put(id, posterUrls.get(0));
            }
        }

        for (TournamentListResponse response : responses) {
            String posterUrl = firstPosterByTournamentId.get(response.getId());
            if (posterUrl != null) {
                response.setPosterUrl(posterUrl);
            }
        }
    }

    private long getConfirmedParticipantCount(Long tournamentId) {
        Long count = participantRepository.countByTournamentIdAndStatus(
            tournamentId, com.futsal.tournament.domain.TournamentParticipant.ParticipantStatus.CONFIRMED
        );
        return count != null ? count : 0L;
    }

    private void populateParticipantSummaries(
        List<TournamentListResponse> responses,
        List<Tournament> tournaments
    ) {
        if (responses == null || responses.isEmpty() || tournaments == null || tournaments.isEmpty()) {
            return;
        }

        List<Long> tournamentIds = tournaments.stream()
            .map(Tournament::getId)
            .collect(Collectors.toList());

        java.util.Map<Long, Long> confirmedCountMap = participantRepository
            .countConfirmedByTournamentIds(tournamentIds)
            .stream()
            .collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> (Long) row[1]
            ));

        java.util.Map<Long, Tournament> tournamentMap = tournaments.stream()
            .collect(Collectors.toMap(Tournament::getId, tournament -> tournament));

        for (TournamentListResponse response : responses) {
            Tournament tournament = tournamentMap.get(response.getId());
            if (tournament == null) {
                continue;
            }
            long confirmedCount = confirmedCountMap.getOrDefault(response.getId(), 0L);
            enrichParticipantSummary(response, tournament, confirmedCount);
        }
    }

    private void enrichParticipantSummary(
        TournamentListResponse response,
        Tournament tournament,
        long confirmedCount
    ) {
        if (tournament == null || response == null) {
            return;
        }

        int confirmedTeamCount = Math.toIntExact(confirmedCount);
        Integer remainingTeamCount = calculateRemainingTeamCount(tournament, confirmedTeamCount);

        response.setConfirmedTeamCount(confirmedTeamCount);
        response.setRemainingTeamCount(remainingTeamCount);
        response.setJoinStatusLabel(buildJoinStatusLabel(tournament, confirmedTeamCount, remainingTeamCount));
    }

    private void enrichParticipantSummary(
        TournamentResponse response,
        Tournament tournament,
        long confirmedCount
    ) {
        if (tournament == null || response == null) {
            return;
        }

        int confirmedTeamCount = Math.toIntExact(confirmedCount);
        Integer remainingTeamCount = calculateRemainingTeamCount(tournament, confirmedTeamCount);

        response.setConfirmedTeamCount(confirmedTeamCount);
        response.setRemainingTeamCount(remainingTeamCount);
        response.setJoinStatusLabel(buildJoinStatusLabel(tournament, confirmedTeamCount, remainingTeamCount));
    }

    private Integer calculateRemainingTeamCount(Tournament tournament, int confirmedTeamCount) {
        if (Boolean.TRUE.equals(tournament.getIsExternal()) || tournament.getMaxTeams() == null) {
            return null;
        }

        return Math.max(0, tournament.getMaxTeams() - confirmedTeamCount);
    }

    private String buildJoinStatusLabel(
        Tournament tournament,
        int confirmedTeamCount,
        Integer remainingTeamCount
    ) {
        if (Boolean.TRUE.equals(tournament.getIsExternal())) {
            return null;
        }
        if (!"OPEN".equalsIgnoreCase(tournament.getRecruitmentStatus())) {
            return null;
        }
        if (remainingTeamCount == null || remainingTeamCount <= 0) {
            return null;
        }
        if (remainingTeamCount <= REMAINING_TEAMS_ALERT_THRESHOLD) {
            return remainingTeamCount + "팀 남음";
        }
        if (tournament.getMaxTeams() != null
            && confirmedTeamCount >= (int) Math.ceil(tournament.getMaxTeams() / 2.0)) {
            return "모집 마감 임박";
        }
        return null;
    }

}
