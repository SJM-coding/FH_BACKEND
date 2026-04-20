package com.futsal.user.domain;

import com.futsal.team.domain.AwardType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 개인 수상 뱃지.
 * 대회 결과 입력 시 TournamentParticipantMember 스냅샷을 기반으로 생성된다.
 * 팀·대회·개최자가 삭제되어도 기록이 보존되도록 모든 참조를 스냅샷으로 저장한다.
 */
@Entity
@Table(name = "user_awards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserAward {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "team_id", nullable = false)
  private Long teamId;

  @Column(name = "team_name", nullable = false, length = 100)
  private String teamName;

  @Column(name = "organizer_user_id", nullable = false)
  private Long organizerUserId;

  @Column(name = "organizer_name", nullable = false, length = 50)
  private String organizerName;

  @Column(name = "tournament_id", nullable = false)
  private Long tournamentId;

  @Column(name = "tournament_name", nullable = false, length = 200)
  private String tournamentName;

  @Enumerated(EnumType.STRING)
  @Column(name = "award_type", nullable = false, length = 20)
  private AwardType awardType;

  @Column(name = "award_date", nullable = false)
  private LocalDate awardDate;

  @Column(nullable = false, updatable = false)
  private LocalDateTime earnedAt;

  @PrePersist
  protected void onCreate() {
    earnedAt = LocalDateTime.now();
  }
}
