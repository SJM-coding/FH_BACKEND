package com.futsal.tournament.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 일정표/대진표 이미지 파싱 결과 응답.
 * 운영자가 프론트에서 내용을 확인 후 저장을 확정한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleParseResponse {

  private List<ParsedMatchDto> matches;

  /** 참가자 목록에 없어서 ID 매핑에 실패한 팀 이름 목록 */
  private List<String> unmappedTeamNames;

  /** 파싱된 경기 수 */
  private int totalCount;

  /** 매핑 실패 경기가 1건 이상이면 true */
  private boolean hasWarning;
}
