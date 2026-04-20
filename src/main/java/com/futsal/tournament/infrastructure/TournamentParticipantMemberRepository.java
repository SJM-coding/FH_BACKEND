package com.futsal.tournament.infrastructure;

import com.futsal.tournament.domain.TournamentParticipantMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentParticipantMemberRepository
    extends JpaRepository<TournamentParticipantMember, Long> {

  /**
   * 참가 레코드에 속한 멤버 스냅샷 조회
   */
  List<TournamentParticipantMember> findByTournamentParticipantId(Long tournamentParticipantId);

  /**
   * 대회 + 팀 기준 멤버 스냅샷 조회 — UserAward 전파 시 사용
   */
  List<TournamentParticipantMember> findByTournamentIdAndTeamId(Long tournamentId, Long teamId);

  /**
   * 유저의 개인 참가 이력 조회
   */
  List<TournamentParticipantMember> findByUserId(Long userId);

  /**
   * 참가 취소 시 스냅샷 삭제
   */
  void deleteByTournamentParticipantId(Long tournamentParticipantId);

  /**
   * 대회 삭제 시 일괄 삭제
   */
  void deleteByTournamentId(Long tournamentId);
}
