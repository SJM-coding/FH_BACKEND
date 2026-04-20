package com.futsal.tournament.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Modal AI 서버가 파싱한 단일 경기 정보.
 * DB 저장 전 운영자 확인용 미리보기 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedMatchDto {

  private Integer round;
  private Integer matchNumber;

  /** 조별리그인 경우 "A", "B" 등. 토너먼트는 null */
  private String groupId;

  private String team1Name;
  private String team2Name;

  /** AI가 파싱한 팀 이름으로 조회한 참가자 ID. 매핑 실패 시 null */
  private Long team1Id;
  private Long team2Id;

  /** ISO-8601 문자열 (YYYY-MM-DDTHH:MM:SS). LocalDateTime 직렬화 이슈 방지용 String 타입 */
  private String scheduledAt;
  private String venueName;

  /** true: 참가자 목록에 없는 팀명 포함 → 운영자 확인 필요 */
  private boolean hasUnmappedTeam;
}
