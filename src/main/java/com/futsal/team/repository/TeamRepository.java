package com.futsal.team.repository;

import com.futsal.team.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {
    
    /**
     * 팀장으로 팀 조회
     */
    List<Team> findByCaptainUserId(Long captainUserId);
    
    /**
     * 지역별 팀 조회
     */
    List<Team> findByRegion(String region);
    
    /**
     * 팀명으로 검색
     */
    List<Team> findByNameContaining(String keyword);
}
