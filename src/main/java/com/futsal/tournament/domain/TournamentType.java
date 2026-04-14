package com.futsal.tournament.domain;

/**
 * 대회 형식 타입
 */
public enum TournamentType {
    SINGLE_ELIMINATION("싱글 엘리미네이션", "토너먼트", "패배 시 즉시 탈락"),
    GROUP_STAGE("조별 리그", "그룹 스테이지", "조별 예선 후 결선 토너먼트"),
    SPLIT_STAGE("풋투풋", "풋투풋", "조별 예선 후 상위/하위 분리 토너먼트"),
    SWISS_SYSTEM("스위스 시스템", "스위스", "같은 승점의 팀끼리 매칭"),
    EXTERNAL("외부 대회", "외부", "외부 사이트에서 진행되는 대회");

    private final String displayName;
    private final String shortName;
    private final String description;

    TournamentType(String displayName, String shortName, String description) {
        this.displayName = displayName;
        this.shortName = shortName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortName() {
        return shortName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 최소 참가 팀 수
     */
    public int getMinimumTeams() {
        switch (this) {
            case SINGLE_ELIMINATION:
                return 4; // 최소 4강
            case GROUP_STAGE:
                return 8; // 최소 2개 조 (각 4팀)
            case SPLIT_STAGE:
                return 16; // 최소 4조×4팀 (풋투풋 유효 구성)
            case SWISS_SYSTEM:
                return 6; // 최소 6팀
            case EXTERNAL:
                return 0; // 외부 대회는 제한 없음
            default:
                return 4;
        }
    }

    /**
     * 권장 팀 수인지 확인
     */
    public boolean isRecommendedTeamCount(int teamCount) {
        switch (this) {
            case SINGLE_ELIMINATION:
                // 2의 거듭제곱 (4, 8, 16, 32...)
                return isPowerOfTwo(teamCount);
            case GROUP_STAGE:
                // 8의 배수 또는 16의 배수 권장
                return teamCount % 8 == 0 || teamCount % 16 == 0;
            case SPLIT_STAGE:
                // 유효한 풋투풋 팀 수: 16(4조×4), 18(3조×6), 20(4조×5)
                return teamCount == 16 || teamCount == 18 || teamCount == 20;
            case SWISS_SYSTEM:
                // 짝수면 권장
                return teamCount % 2 == 0;
            case EXTERNAL:
                // 외부 대회는 항상 true
                return true;
            default:
                return false;
        }
    }

    private boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    /**
     * 필요한 라운드 수 계산
     */
    public int calculateRounds(int teamCount) {
        switch (this) {
            case SINGLE_ELIMINATION:
                return (int) Math.ceil(Math.log(teamCount) / Math.log(2));
            case GROUP_STAGE:
                // 조별 경기 + 결선 토너먼트
                int groupSize = 4;
                int knockoutTeams = (teamCount / groupSize) / 2; // 각 조 상위 2팀
                return groupSize - 1
                    + (int) Math.ceil(Math.log(knockoutTeams) / Math.log(2));
            case SPLIT_STAGE:
                // 조별(1) + play-in(1) + 상위/하위 토너먼트(3~4)
                return 5;
            case SWISS_SYSTEM:
                // 일반적으로 log2(팀 수) 라운드
                return Math.max(3, (int) Math.ceil(Math.log(teamCount) / Math.log(2)));
            case EXTERNAL:
                // 외부 대회는 라운드 개념 없음
                return 0;
            default:
                return 1;
        }
    }
}
