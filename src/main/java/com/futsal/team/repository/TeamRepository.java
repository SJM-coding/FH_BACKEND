package com.futsal.team.repository;

import com.futsal.team.domain.Team;
import com.futsal.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    
    /**
     * 팀장으로 팀 조회
     */
    List<Team> findByCaptain(User captain);

    /**
     * 팀 + 팀장 함께 조회 (N+1 방지)
     */
    @Query("SELECT t FROM Team t JOIN FETCH t.captain WHERE t.id = :id")
    Optional<Team> findByIdWithCaptain(@Param("id") Long id);
    
    /**
     * 지역별 팀 조회
     */
    List<Team> findByRegion(String region);
    
    /**
     * 팀명으로 검색
     */
    List<Team> findByNameContaining(String keyword);
}
