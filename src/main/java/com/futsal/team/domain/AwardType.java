package com.futsal.team.domain;

/**
 * 수상 종류
 */
public enum AwardType {
    CHAMPION("우승"),
    RUNNER_UP("준우승"),
    THIRD_PLACE("3위"),
    FOURTH_PLACE("4위"),
    MVP("MVP"),
    TOP_SCORER("득점왕"),
    BEST_GOALKEEPER("베스트 골키퍼"),
    FAIR_PLAY("페어플레이상"),
    OTHER("기타");

    private final String displayName;

    AwardType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
