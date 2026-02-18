package com.futsal.tournament.domain;

/**
 * 대회 형식 타입
 */
public enum TournamentType {
    SINGLE_ELIMINATION("싱글 엘리미네이션", "토너먼트", "패배 시 즉시 탈락"),
    GROUP_STAGE("조별 리그", "그룹 스테이지", "조별 예선 후 결선 토너먼트"),
    SWISS_SYSTEM("스위스 시스템", "스위스", "같은 승점의 팀끼리 매칭");

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
            case SWISS_SYSTEM:
                return 6; // 최소 6팀
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
            case SWISS_SYSTEM:
                // 짝수면 권장
                return teamCount % 2 == 0;
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
                return groupSize - 1 + (int) Math.ceil(Math.log(knockoutTeams) / Math.log(2));
            case SWISS_SYSTEM:
                // 일반적으로 log2(팀 수) 라운드
                return Math.max(3, (int) Math.ceil(Math.log(teamCount) / Math.log(2)));
            default:
                return 1;
        }
    }
}
