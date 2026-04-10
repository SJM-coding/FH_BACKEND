package com.futsal.tournament.domain;

import com.futsal.tournament.event.TournamentTitleChangedEvent;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.futsal.user.domain.User;
import com.futsal.tournament.infrastructure.StringListConverter;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

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
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class Tournament extends AbstractAggregateRoot<Tournament> {

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

    @Column(columnDefinition = "JSON")
    @Convert(converter = StringListConverter.class)
    @Builder.Default
    private List<String> posterUrls = new ArrayList<>(); // 여러 포스터 URL (JSON 배열로 저장)

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String recruitmentStatus = "OPEN"; // 모집 상태: OPEN(모집중), CLOSED(마감)

    /**
     * 참가 코드 / 운영진 코드 — ShareCode VO에 위임
     * 컬럼은 그대로 participant_code / staff_code 유지 (마이그레이션 불필요)
     */
    @Embedded
    private ShareCode shareCode;

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

    // ── ShareCode 위임 접근자 (기존 호출 코드 호환) ──────────────────

    /** 참가 코드 반환 (외부 대회는 null) */
    public String getParticipantCode() {
        return shareCode != null ? shareCode.getParticipantCode() : null;
    }

    /** 운영진 코드 반환 (미발급 시 null) */
    public String getStaffCode() {
        return shareCode != null ? shareCode.getStaffCode() : null;
    }

    /**
     * 운영진 코드 발급 — Tournament Aggregate의 도메인 행위
     * 이미 발급된 경우 아무 변화 없음.
     *
     * @param isUnique staffCode 고유성 검증 술어 (Application Service가 주입)
     */
    public void issueStaffCode(Predicate<String> isUnique) {
        if (this.shareCode == null) {
            throw new IllegalStateException(
                "참가 코드가 없는 대회에는 운영진 코드를 발급할 수 없습니다.");
        }
        this.shareCode = this.shareCode.issueStaffCode(isUnique);
    }

    // ── 불변식 / 상태 검증 ────────────────────────────────────────────

    /**
     * 참가 신청 가능 여부 (대진표 생성 여부는 Bracket aggregate에서 확인)
     */
    public boolean isJoinable() {
        return !Boolean.TRUE.equals(this.isExternal)
            && Boolean.TRUE.equals(this.allowJoin)
            && "OPEN".equalsIgnoreCase(this.recruitmentStatus);
    }

    /**
     * 결선 토너먼트 경기 여부 (경기 결과 검증 시 Application Service가 사용)
     */
    public boolean isKnockoutMatch(TournamentMatch match) {
        return this.tournamentType == TournamentType.SINGLE_ELIMINATION
            || (this.tournamentType == TournamentType.GROUP_STAGE
                && match.getGroupId() == null);
    }

    /**
     * 대회 제목 변경 — TeamAward 역정규화 필드 동기화를 위해 이벤트 발행
     */
    public void changeTitle(String title) {
        this.title = title;
        registerEvent(new TournamentTitleChangedEvent(this.id, title));
    }
}
