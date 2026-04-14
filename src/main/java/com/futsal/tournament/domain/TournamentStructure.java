package com.futsal.tournament.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 대회 구조 Value Object
 *
 * 대회 형식(tournamentType)과 관련된 구조 설정을 하나의 개념 단위로 묶는다.
 * Tournament 엔티티에 @Embedded 되어 같은 테이블(tournaments)에 저장된다.
 *
 * 컬럼 이름은 기존 tournaments 테이블 컬럼과 동일하므로 스키마 변경 없음.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class TournamentStructure {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TournamentType tournamentType;

    @Column(nullable = false)
    private Integer maxTeams;

    /** 조별리그 / 풋투풋: 조 개수 */
    @Column
    private Integer groupCount;

    /** 조별리그 / 풋투풋: 조당 팀 수 */
    @Column
    private Integer teamsPerGroup;

    /**
     * 조별리그에서 각 조당 결선 진출 팀 수 (기본값: 2)
     * 풋투풋(SPLIT_STAGE)에서는 사용하지 않음.
     */
    @Column
    private Integer advanceCount = 2;

    /** 스위스 시스템: 라운드 수 */
    @Column
    private Integer swissRounds;

    @Builder
    public TournamentStructure(
        TournamentType tournamentType,
        Integer maxTeams,
        Integer groupCount,
        Integer teamsPerGroup,
        Integer advanceCount,
        Integer swissRounds
    ) {
        this.tournamentType = tournamentType;
        this.maxTeams = maxTeams;
        this.groupCount = groupCount;
        this.teamsPerGroup = teamsPerGroup;
        this.advanceCount = (advanceCount != null) ? advanceCount : 2;
        this.swissRounds = swissRounds;
    }

    // ── 팩토리 메서드 ──────────────────────────────────────────────────

    public static TournamentStructure singleElimination(int maxTeams) {
        return TournamentStructure.builder()
            .tournamentType(TournamentType.SINGLE_ELIMINATION)
            .maxTeams(maxTeams)
            .build();
    }

    public static TournamentStructure groupStage(
        int maxTeams, Integer groupCount, Integer teamsPerGroup, Integer advanceCount
    ) {
        return TournamentStructure.builder()
            .tournamentType(TournamentType.GROUP_STAGE)
            .maxTeams(maxTeams)
            .groupCount(groupCount)
            .teamsPerGroup(teamsPerGroup)
            .advanceCount(advanceCount != null ? advanceCount : 2)
            .build();
    }

    /** 풋투풋(상위/하위 분리 토너먼트) — advanceCount 불필요 */
    public static TournamentStructure splitStage(
        int maxTeams, Integer groupCount, Integer teamsPerGroup
    ) {
        return TournamentStructure.builder()
            .tournamentType(TournamentType.SPLIT_STAGE)
            .maxTeams(maxTeams)
            .groupCount(groupCount)
            .teamsPerGroup(teamsPerGroup)
            .advanceCount(2)
            .build();
    }

    public static TournamentStructure swissSystem(int maxTeams, Integer swissRounds) {
        return TournamentStructure.builder()
            .tournamentType(TournamentType.SWISS_SYSTEM)
            .maxTeams(maxTeams)
            .swissRounds(swissRounds)
            .build();
    }

    public static TournamentStructure external() {
        return TournamentStructure.builder()
            .tournamentType(TournamentType.EXTERNAL)
            .maxTeams(0)
            .build();
    }

    // ── 도메인 쿼리 메서드 ─────────────────────────────────────────────

    /**
     * 조별 예선이 있는 대회 여부 (GROUP_STAGE, SPLIT_STAGE)
     */
    public boolean isGroupBased() {
        return tournamentType == TournamentType.GROUP_STAGE
            || tournamentType == TournamentType.SPLIT_STAGE;
    }

    /**
     * 풋투풋(상위/하위 분리 토너먼트) 여부
     */
    public boolean isSplit() {
        return tournamentType == TournamentType.SPLIT_STAGE;
    }

    /**
     * 외부 대회 여부
     */
    public boolean isExternal() {
        return tournamentType == TournamentType.EXTERNAL;
    }
}
