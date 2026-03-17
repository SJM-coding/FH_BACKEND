package com.futsal.team.domain;

/**
 * 수상 종류
 */
public enum AwardType {
    CHAMPION("우승"),
    RUNNER_UP("준우승"),
    THIRD_PLACE("3위"),
    FOURTH_PLACE("4위"),
    PARTICIPATION("참가");

    private final String displayName;

    AwardType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
