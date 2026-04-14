package com.futsal.tournament.presentation.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 상위/하위 분리 토너먼트 팀 배정 요청
 *
 * upperTeamIds:      상위 토너먼트 진출 팀 ID 목록 (play-in 팀 제외)
 * lowerTeamIds:      하위 토너먼트 진출 팀 ID 목록 (play-in 팀 제외)
 * upperPlayInTeamIds: 상위 play-in 경기에 참가할 팀 ID 2개
 * lowerPlayInTeamIds: 하위 play-in 경기에 참가할 팀 ID 2개
 *
 * totalTeams가 2의 거듭제곱이면 playIn 목록이 비어 있어도 된다.
 */
@Data
@NoArgsConstructor
public class SplitBracketRequest {
    private List<Long> upperTeamIds;
    private List<Long> lowerTeamIds;
    private List<Long> upperPlayInTeamIds; // 0개(play-in 없음) or 2개
    private List<Long> lowerPlayInTeamIds; // 0개(play-in 없음) or 2개
}
