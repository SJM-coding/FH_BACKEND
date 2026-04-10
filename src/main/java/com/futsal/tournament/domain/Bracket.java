package com.futsal.tournament.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 대진표 Aggregate Root
 *
 * Tournament와 1:1 관계 (tournament_id = PK)
 * Tournament 엔티티의 bracket 관련 필드를 이 Aggregate로 점진적 이전
 * 현재는 dual write 방식으로 Tournament 필드와 병행 운용
 */
@Entity
@Table(name = "brackets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bracket {

    /**
     * Tournament ID와 동일 (shared PK, 1:1)
     */
    @Id
    @Column(name = "tournament_id")
    private Long tournamentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BracketType type = BracketType.AUTO;

    @Column(nullable = false)
    private boolean generated = false;

    /**
     * 결선 방식: SINGLE(단일 토너먼트), SPLIT(상위/하위 분리 토너먼트)
     */
    @Column(name = "knockout_type", nullable = false, length = 10)
    private String knockoutType = "SINGLE";

    /**
     * 상위/하위 분리 시 조당 상위 토너먼트 팀 수
     */
    @Column(name = "split_count")
    private Integer splitCount;

    /**
     * 대진표 이미지 URL 목록 (MANUAL 타입일 때 사용)
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "bracket_images",
        joinColumns = @JoinColumn(name = "tournament_id")
    )
    @Column(name = "image_url", length = 500)
    private List<String> imageUrls = new ArrayList<>();

    /**
     * 대회 생성 시 기본 Bracket 생성 (AUTO 방식)
     */
    public static Bracket createDefault(Long tournamentId) {
        Bracket bracket = new Bracket();
        bracket.tournamentId = tournamentId;
        bracket.type = BracketType.AUTO;
        bracket.generated = false;
        bracket.imageUrls = new ArrayList<>();
        return bracket;
    }

    /**
     * 기존 Tournament 데이터 기반으로 Bracket 복원 (마이그레이션용)
     */
    public static Bracket restore(
        Long tournamentId,
        BracketType type,
        boolean generated,
        List<String> imageUrls
    ) {
        Bracket bracket = new Bracket();
        bracket.tournamentId = tournamentId;
        bracket.type = type != null ? type : BracketType.AUTO;
        bracket.generated = generated;
        bracket.imageUrls = imageUrls != null
            ? new ArrayList<>(imageUrls) : new ArrayList<>();
        return bracket;
    }

    /**
     * 이미지 업로드 모드로 전환
     */
    public void switchToManual(List<String> urls) {
        this.type = BracketType.MANUAL;
        this.imageUrls = urls != null ? new ArrayList<>(urls) : new ArrayList<>();
        this.generated = !this.imageUrls.isEmpty();
    }

    /**
     * 자동 생성 모드로 전환 (이미지 초기화)
     */
    public void switchToAuto() {
        this.type = BracketType.AUTO;
        this.imageUrls = new ArrayList<>();
        this.generated = false;
    }

    /**
     * 자동 대진표 생성 완료 처리
     */
    public void markGenerated() {
        this.generated = true;
    }

    public boolean isManual() {
        return this.type == BracketType.MANUAL;
    }

    public boolean isGenerated() {
        return this.generated;
    }

    public boolean isSplit() {
        return "SPLIT".equals(this.knockoutType);
    }

    public void configureSplit(int splitCount) {
        this.knockoutType = "SPLIT";
        this.splitCount = splitCount;
    }
}
