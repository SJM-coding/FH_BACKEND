package com.futsal.tournament.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 대진표 이미지 AI 파싱 결과 응답.
 * 조별 팀 배정 목록과 매핑 실패 팀 정보를 포함한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupParseResponse {

  /** 조별 팀 배정 목록 */
  private List<GroupAssignmentDto> assignments;

  /** 참가자 목록에 없어서 ID 매핑에 실패한 팀 이름 목록 */
  private List<String> unmappedTeamNames;

  /** 파싱된 팀 배정 수 */
  private int totalCount;

  /** 매핑 실패 팀이 1개 이상이면 true */
  private boolean hasWarning;
}
