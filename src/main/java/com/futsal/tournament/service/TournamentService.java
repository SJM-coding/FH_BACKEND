package com.futsal.tournament.service;

import com.futsal.tournament.domain.Gender;
import com.futsal.tournament.domain.PlayerType;
import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.domain.TournamentType;
import com.futsal.tournament.dto.TournamentCreateRequest;
import com.futsal.tournament.dto.TournamentListResponse;
import com.futsal.tournament.dto.TournamentResponse;
import com.futsal.tournament.dto.TournamentUpdateRequest;
import com.futsal.tournament.repository.TournamentRepository;
import com.futsal.user.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TournamentService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    private final TournamentRepository tournamentRepository;
    private final String defaultPosterUrl;

    public TournamentService(TournamentRepository tournamentRepository,
                             @Value("${app.poster.default-url:}") String defaultPosterUrl) {
        this.tournamentRepository = tournamentRepository;
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

        // 참가 코드 생성 (내부 대회만)
        String participantCode = null;
        if (!isExternal) {
            participantCode = generateUniqueParticipantCode();
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
                .swissRounds(request.getSwissRounds())
                .posterUrls(normalizePosterUrls(request.getPosterUrls()))
                .isExternal(isExternal)
                .externalUrl(request.getExternalUrl())
                .allowJoin(allowJoin)
                .participantCode(participantCode)
                .registeredBy(registeredBy)
                .build();

        Tournament saved = tournamentRepository.save(tournament);
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

        // 수정
        if (request.getTitle() != null) tournament.setTitle(request.getTitle());
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
     * Phase 2-3: 내가 등록한 대회 목록
     */
    @Transactional(readOnly = true)
    public List<TournamentListResponse> getMyTournaments(User user) {
        List<TournamentListResponse> responses = tournamentRepository.findListByRegisteredBy(user);
        populatePosterUrls(responses);
        return responses;
    }

    /**
     * Phase 2-5: 날짜 지난 대회 자동 삭제
     */
    @Transactional
    public int deleteExpiredTournaments() {
        LocalDate today = LocalDate.now();
        List<Tournament> expiredTournaments = tournamentRepository.findByTournamentDateBefore(today);
        int count = expiredTournaments.size();
        tournamentRepository.deleteAll(expiredTournaments);
        return count;
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
        return new TournamentResponse(
                tournament.getId(),
                tournament.getTitle(),
                tournament.getTournamentDate(),
                tournament.getLocation(),
                tournament.getPlayerType(),
                tournament.getGender(),
                tournament.getDescription(),
                tournament.getViewCount(),
                tournament.getOriginalLink(),
                tournament.getTournamentType().name(),
                tournament.getMaxTeams(),
                tournament.getGroupCount(),
                tournament.getTeamsPerGroup(),
                tournament.getSwissRounds(),
                tournament.getBracketGenerated(),
                tournament.getBracketType() != null ? tournament.getBracketType().name() : "AUTO",
                tournament.getBracketImageUrls() != null ? tournament.getBracketImageUrls() : new ArrayList<>(),
                tournament.getPosterUrls() != null ? tournament.getPosterUrls() : new ArrayList<>(),
                tournament.getRecruitmentStatus(),
                tournament.getRegisteredBy() != null ? tournament.getRegisteredBy().getId() : null,
                tournament.getRegisteredBy() != null ? tournament.getRegisteredBy().getNickname() : null,
                tournament.getCreatedAt(),
                tournament.getIsExternal(),
                tournament.getExternalUrl(),
                tournament.getParticipantCode(),
                tournament.getStaffCode()
        );
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
     * 대회 목록 조회 (gender, playerType, limit 필터링 가능)
     * - limit이 있고 gender/playerType 둘 다 있으면 DB에서 LIMIT 적용
     * - limit이 없으면 전체 조회 후 최신순 정렬
     */
    @Transactional(readOnly = true)
    public List<TournamentListResponse> getTournaments(String genderStr, String playerTypeStr, Integer limit) {
        Gender gender = parseGender(genderStr);
        PlayerType playerType = parsePlayerType(playerTypeStr);

        List<TournamentListResponse> responses;

        // limit이 있고 gender/playerType 둘 다 있으면 DB 레벨에서 LIMIT 적용
        if (limit != null && limit > 0 && gender != null && playerType != null) {
            responses = tournamentRepository.findListByGenderAndPlayerTypeWithLimit(
                    gender, playerType, PageRequest.of(0, limit));
            populatePosterUrls(responses);
            return responses;
        }

        // limit이 있고 gender만 있으면 (MIXED 등) DB 레벨에서 LIMIT 적용
        if (limit != null && limit > 0 && gender != null && playerType == null) {
            responses = tournamentRepository.findListByGenderWithLimit(
                    gender, PageRequest.of(0, limit));
            populatePosterUrls(responses);
            return responses;
        }

        // 그 외: 전체 조회
        if (gender != null && playerType != null) {
            responses = tournamentRepository.findListByGenderAndPlayerType(gender, playerType);
        } else if (gender != null) {
            responses = tournamentRepository.findListByGender(gender);
        } else if (playerType != null) {
            responses = tournamentRepository.findListByPlayerType(playerType);
        } else {
            responses = tournamentRepository.findListAll();
        }
        populatePosterUrls(responses);

        // 최신순 정렬 (날짜 내림차순)
        responses.sort((a, b) -> b.getTournamentDate().compareTo(a.getTournamentDate()));
        return responses;
    }
    
    /**
     * 전체 대회 목록 조회 (날짜순) - 호환성을 위해 유지
     */
    @Transactional(readOnly = true)
    public List<TournamentListResponse> getAllTournaments() {
        return getTournaments(null, null, null);
    }

    /**
     * 키워드 검색
     */
    @Transactional(readOnly = true)
    public List<TournamentListResponse> searchTournaments(String keyword) {
        List<TournamentListResponse> responses = tournamentRepository.findListByKeyword(keyword);
        populatePosterUrls(responses);
        return responses;
    }

    /**
     * 단일 대회 조회 (조회수 증가)
     */
    @Transactional
    public TournamentResponse getTournamentById(Long id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("대회를 찾을 수 없습니다: " + id));

        // 조회수 원자적 증가 (race condition 방지)
        tournamentRepository.incrementViewCount(id);

        return toResponse(tournament);
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

        tournamentRepository.deleteById(id);
    }

    private void populatePosterUrls(List<TournamentListResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }

        List<Long> ids = responses.stream()
                .map(TournamentListResponse::getId)
                .collect(Collectors.toList());

        List<Object[]> rows = tournamentRepository.findPosterUrlsByTournamentIds(ids);
        if (rows == null || rows.isEmpty()) {
            return;
        }

        // keep the first poster encountered per tournament (same as previous behavior)
        java.util.Map<Long, String> firstPosterByTournamentId = new java.util.HashMap<>();
        for (Object[] row : rows) {
            Long tournamentId = (Long) row[0];
            String posterUrl = (String) row[1];
            firstPosterByTournamentId.putIfAbsent(tournamentId, posterUrl);
        }

        for (TournamentListResponse response : responses) {
            String posterUrl = firstPosterByTournamentId.get(response.getId());
            if (posterUrl != null) {
                response.setPosterUrl(posterUrl);
            }
        }
    }

    /**
     * 고유한 참가 코드 생성
     */
    private String generateUniqueParticipantCode() {
        String code;
        do {
            code = generateRandomCode();
        } while (tournamentRepository.existsByParticipantCode(code));
        return code;
    }

    /**
     * 고유한 운영진 코드 생성
     */
    public String generateUniqueStaffCode() {
        String code;
        do {
            code = generateRandomCode();
        } while (tournamentRepository.existsByStaffCode(code));
        return code;
    }

    /**
     * 랜덤 코드 생성
     */
    private String generateRandomCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = random.nextInt(CODE_CHARS.length());
            code.append(CODE_CHARS.charAt(index));
        }
        return code.toString();
    }
}
