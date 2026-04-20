package com.futsal.user.infrastructure;

import com.futsal.user.domain.UserAward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserAwardRepository extends JpaRepository<UserAward, Long> {

  /**
   * 유저의 수상 뱃지 목록 조회 (최신순)
   */
  List<UserAward> findByUserIdOrderByAwardDateDesc(Long userId);

  /**
   * 대회 결과 삭제 시 관련 뱃지 일괄 삭제
   */
  void deleteByTournamentId(Long tournamentId);

  /**
   * 유저 탈퇴 시 뱃지 일괄 삭제
   */
  void deleteByUserId(Long userId);

  boolean existsByTournamentId(Long tournamentId);
}
