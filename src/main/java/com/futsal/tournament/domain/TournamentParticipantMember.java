package com.futsal.tournament.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 대회 참가 시점 팀원 스냅샷.
 * 참가 신청 당시 ACTIVE 멤버를 기록해 개인 참가 이력 조회와
 * 결과 입력 시 UserAward 전파의 대상 목록으로 활용한다.
 * 팀원이 이후 교체되어도 당시 구성원 기준의 이력이 보존된다.
 */
@Entity
@Table(
  name = "tournament_participant_members",
  uniqueConstraints = @UniqueConstraint(
    name = "uk_participant_member",
    columnNames = {"tournament_participant_id", "user_id"}
  )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TournamentParticipantMember {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tournament_participant_id", nullable = false)
  private Long tournamentParticipantId;

  @Column(name = "tournament_id", nullable = false)
  private Long tournamentId;

  @Column(name = "team_id", nullable = false)
  private Long teamId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, updatable = false)
  private LocalDateTime snapshottedAt;

  @PrePersist
  protected void onCreate() {
    snapshottedAt = LocalDateTime.now();
  }
}
