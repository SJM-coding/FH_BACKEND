package com.futsal.tournament.service;

import com.futsal.tournament.dto.TournamentCreateRequest;
import com.futsal.tournament.dto.TournamentListResponse;
import com.futsal.tournament.dto.TournamentResponse;
import com.futsal.tournament.dto.TournamentUpdateRequest;
import com.futsal.tournament.domain.Tournament;
import com.futsal.tournament.domain.TournamentType;
import com.futsal.user.domain.User;
import com.futsal.tournament.repository.TournamentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TournamentService {

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
        // 외부 대회일 경우 allowJoin = false 강제
        Boolean isExternal = request.getIsExternal() != null ? request.getIsExternal() : false;
        Boolean allowJoin = isExternal ? false : true;
        
        Tournament tournament = Tournament.builder()
                .title(request.getTitle())
                .tournamentDate(request.getTournamentDate())
                .location(request.getLocation())
                .playerType(request.getPlayerType())
                .gender(request.getGender())
                .description(request.getDescription())
                .viewCount(0)
                .originalLink(request.getOriginalLink())
                .tournamentType(request.getTournamentType() != null
                        ? request.getTournamentType()
                        : TournamentType.SINGLE_ELIMINATION)
                .maxTeams(request.getMaxTeams() != null ? request.getMaxTeams() : 16)
                .groupCount(request.getGroupCount())
                .teamsPerGroup(request.getTeamsPerGroup())
                .swissRounds(request.getSwissRounds())
                .posterUrls(normalizePosterUrls(request.getPosterUrls()))
                .isExternal(isExternal)
                .externalUrl(request.getExternalUrl())
                .allowJoin(allowJoin)
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
            // 외부 대회로 변경 시 allowJoin = false
            if (request.getIsExternal()) {
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
                tournament.getTournamentType() != null ? tournament.getTournamentType().name() : null,
                tournament.getMaxTeams(),
                tournament.getGroupCount(),
                tournament.getTeamsPerGroup(),
                tournament.getSwissRounds(),
                tournament.getBracketGenerated(),
                tournament.getPosterUrls() != null ? tournament.getPosterUrls() : new ArrayList<>(),
                tournament.getRecruitmentStatus(),
                tournament.getRegisteredBy() != null ? tournament.getRegisteredBy().getId() : null,
                tournament.getRegisteredBy() != null ? tournament.getRegisteredBy().getNickname() : null,
                tournament.getCreatedAt(),
                tournament.getIsExternal(),
                tournament.getExternalUrl()
        );
    }

    /**
     * 대회 목록 조회 (gender, playerType, limit 필터링 가능)
     * - limit이 있으면 가까운 날짜순으로 정렬
     * - limit이 없으면 최신순으로 정렬
     */
    @Transactional(readOnly = true)
    public List<TournamentListResponse> getTournaments(String gender, String playerType, Integer limit) {
        List<TournamentListResponse> responses = tournamentRepository.findListAll();
        populatePosterUrls(responses);
        
        // gender 필터링 (TournamentListResponse에 gender/playerType 필드 포함됨)
        if (gender != null) {
            responses = responses.stream()
                .filter(r -> gender.equals(r.getGender()))
                .collect(Collectors.toList());
        }
        
        // playerType 필터링
        if (playerType != null) {
            responses = responses.stream()
                .filter(r -> playerType.equals(r.getPlayerType()))
                .collect(Collectors.toList());
        }
        
        // 정렬: limit이 있으면 가까운 날짜순, 없으면 최신순
        LocalDate today = LocalDate.now();
        if (limit != null && limit > 0) {
            // 가까운 날짜순 (오늘과의 차이 절대값으로 정렬)
            responses.sort((a, b) -> {
                long diffA = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(today, a.getTournamentDate()));
                long diffB = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(today, b.getTournamentDate()));
                return Long.compare(diffA, diffB);
            });
            // limit 개수만 반환
            return responses.stream().limit(limit).collect(Collectors.toList());
        } else {
            // 최신순 (날짜 내림차순)
            responses.sort((a, b) -> b.getTournamentDate().compareTo(a.getTournamentDate()));
            return responses;
        }
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
        
        // 조회수 증가
        tournament.setViewCount(tournament.getViewCount() + 1);
        tournamentRepository.save(tournament);
        
        return toResponse(tournament);
    }

    /**
     * 공유코드로 대회 조회 (조회수 증가 없이)
     */
    @Transactional(readOnly = true)
    public TournamentResponse getTournamentByShareCode(String shareCode) {
        Tournament tournament = tournamentRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new RuntimeException("유효하지 않은 공유코드입니다: " + shareCode));
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
}
