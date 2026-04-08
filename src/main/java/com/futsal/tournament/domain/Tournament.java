package com.futsal.tournament.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.futsal.user.domain.User;
import com.futsal.tournament.infrastructure.StringListConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "tournaments",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_tournament_duplicate",
        columnNames = {"title", "tournament_date", "user_id","gender","playerType"}
    )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private LocalDate tournamentDate;

    @Column(nullable = false, length = 100)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlayerType playerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int viewCount;

    @Column(nullable = false, length = 500)
    private String originalLink = ""; // 참고 링크 (선택, 빈 문자열 허용)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TournamentType tournamentType;

    @Column(nullable = false)
    private Integer maxTeams;

    @Column
    private Integer groupCount;

    @Column
    private Integer teamsPerGroup;

    /**
     * 조별리그에서 각 조당 결선 진출 팀 수 (기본값: 2)
     */
    @Column
    @Builder.Default
    private Integer advanceCount = 2;

    @Column
    private Integer swissRounds;

    @Column(nullable = false)
    @Builder.Default
    private Boolean bracketGenerated = false;

    /**
     * 대진표 생성 방식
     * AUTO: 시스템 자동 생성 (TournamentMatch 테이블 사용)
     * MANUAL: 이미지 직접 업로드
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    @Builder.Default
    private BracketType bracketType = BracketType.AUTO;

    /**
     * 대진표 이미지 URL 목록 (MANUAL 타입일 때 사용)
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tournament_bracket_images", joinColumns = @JoinColumn(name = "tournament_id"))
    @Column(name = "image_url", length = 500)
    @Builder.Default
    private List<String> bracketImageUrls = new ArrayList<>();

    @Column(columnDefinition = "JSON")
    @Convert(converter = StringListConverter.class)
    @Builder.Default
    private List<String> posterUrls = new ArrayList<>(); // 여러 포스터 URL (JSON 배열로 저장)

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String recruitmentStatus = "OPEN"; // 모집 상태: OPEN(모집중), CLOSED(마감)

    /**
     * 참가 코드: 참가팀이 대회에 참가 신청할 때 사용
     * 대회 생성 시 자동 생성
     */
    @Column(length = 8, unique = true)
    private String participantCode;

    /**
     * 운영진 코드: 운영진이 점수 입력 페이지에 접근할 때 사용
     * 대회 확정 시 생성
     */
    @Column(length = 8, unique = true)
    private String staffCode;

    @Column(nullable = false)
    @Builder.Default
    private Boolean allowJoin = true;

    /**
     * 외부 대회 여부
     * true: 외부 대회 (포스터만 공유, 참가 불가)
     * false: 내부 대회 (참가 가능)
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isExternal = false;

    /**
     * 외부 대회 URL
     * isExternal=true일 때만 사용
     */
    @Column(length = 500)
    private String externalUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User registeredBy; // 등록자

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (posterUrls == null) {
            posterUrls = new ArrayList<>();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 등록자 확인
     */
    public boolean isRegisteredBy(Long userId) {
        return this.registeredBy != null && this.registeredBy.getId().equals(userId);
    }

    public Long getRegisteredById() {
        return this.registeredBy != null ? this.registeredBy.getId() : null;
    }

    /**
     * 포스터 추가
     */
    public void addPosterUrl(String posterUrl) {
        if (posterUrls == null) {
            posterUrls = new ArrayList<>();
        }
        posterUrls.add(posterUrl);
    }

    /**
     * 포스터 목록 설정
     */
    public void setPosterUrls(List<String> posterUrls) {
        this.posterUrls = posterUrls != null ? posterUrls : new ArrayList<>();
    }

    /**
     * 대진표 이미지 목록 설정
     */
    public void setBracketImageUrls(List<String> bracketImageUrls) {
        this.bracketImageUrls = bracketImageUrls != null ? bracketImageUrls : new ArrayList<>();
    }

    /**
     * 대진표를 이미지 모드로 전환
     */
    public void switchToManualBracket(List<String> imageUrls) {
        this.bracketType = BracketType.MANUAL;
        this.bracketImageUrls = imageUrls != null ? imageUrls : new ArrayList<>();
        this.bracketGenerated = !this.bracketImageUrls.isEmpty();
    }

    /**
     * 대진표를 자동 생성 모드로 전환
     */
    public void switchToAutoBracket() {
        this.bracketType = BracketType.AUTO;
        this.bracketImageUrls = new ArrayList<>();
        this.bracketGenerated = false;
    }

    /**
     * 대진표가 이미지 모드인지 확인
     */
    public boolean isManualBracket() {
        return this.bracketType == BracketType.MANUAL;
    }
}
