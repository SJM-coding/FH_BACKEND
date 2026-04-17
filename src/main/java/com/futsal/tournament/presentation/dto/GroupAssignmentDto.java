package com.futsal.tournament.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 파싱 결과 — 조별 팀 배정 항목.
 * groupId(조 이름)와 teamName(팀명)을 담으며,
 * 운영자 확정 시 teamId가 매핑된 항목만 저장된다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupAssignmentDto {

  /** 조 이름 (A, B, C ... 또는 1조, 2조 ...) */
  private String groupId;

  /** AI가 인식한 팀 이름 */
  private String teamName;

  /** 참가자 목록과 매핑된 팀 ID (매핑 실패 시 null) */
  private Long teamId;

  /** 팀 ID 매핑 실패 여부 */
  private boolean unmapped;
}
