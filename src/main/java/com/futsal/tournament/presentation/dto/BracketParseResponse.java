package com.futsal.tournament.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 대진표 이미지 파싱 결과 응답.
 * 운영자가 팀 매칭을 확인 후 내부 경기 생성 로직을 호출한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BracketParseResponse {

  private List<ParsedMatchDto> matches;

  /** 참가자 목록에 없어서 ID 매핑에 실패한 팀 이름 목록 */
  private List<String> unmappedTeamNames;

  /** 파싱된 경기 수 */
  private int totalCount;

  /** 매핑 실패 팀이 1개 이상이면 true */
  private boolean hasWarning;
}
