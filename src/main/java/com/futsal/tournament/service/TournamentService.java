package com.futsal.tournament.service;

import com.futsal.tournament.dto.TournamentCreateRequest;
import com.futsal.tournament.dto.TournamentResponse;
import com.futsal.tournament.dto.TournamentUpdateRequest;
import com.futsal.tournament.domain.Tournament;
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
        Tournament tournament = Tournament.builder()
                .title(request.getTitle())
                .tournamentDate(request.getTournamentDate())
                .location(request.getLocation())
                .playerType(request.getPlayerType())
                .gender(request.getGender())
                .description(request.getDescription())
                .viewCount(0)
                .originalLink(request.getOriginalLink())
                .posterUrls(normalizePosterUrls(request.getPosterUrls()))
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
        
        Tournament saved = tournamentRepository.save(tournament);
        return toResponse(saved);
    }

    /**
     * Phase 2-3: 내가 등록한 대회 목록
     */
    @Transactional(readOnly = true)
    public List<TournamentResponse> getMyTournaments(User user) {
        return tournamentRepository.findByRegisteredByOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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
                tournament.getPosterUrls() != null ? tournament.getPosterUrls() : new ArrayList<>(),
                tournament.getRecruitmentStatus(),
                tournament.getRegisteredBy() != null ? tournament.getRegisteredBy().getId() : null,
                tournament.getRegisteredBy() != null ? tournament.getRegisteredBy().getNickname() : null,
                tournament.getCreatedAt()
        );
    }

    /**
     * 전체 대회 목록 조회 (날짜순)
     */
    @Transactional(readOnly = true)
    public List<TournamentResponse> getAllTournaments() {
        return tournamentRepository.findAllByOrderByTournamentDateAsc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 키워드 검색
     */
    @Transactional(readOnly = true)
    public List<TournamentResponse> searchTournaments(String keyword) {
        return tournamentRepository.findByKeyword(keyword)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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
}
