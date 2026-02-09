package com.futsal.team.repository;

import com.futsal.team.domain.Team;
import com.futsal.team.domain.TeamMember;
import com.futsal.user.domain.User;
import com.futsal.team.domain.TeamMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    
    /**
     * 팀의 모든 멤버 조회
     */
    List<TeamMember> findByTeam(Team team);
    
    /**
     * 팀의 활성 멤버만 조회
     */
    List<TeamMember> findByTeamAndStatus(Team team, TeamMemberStatus status);
    
    /**
     * 사용자가 속한 팀 조회
     */
    List<TeamMember> findByUserAndStatus(User user, TeamMemberStatus status);
    
    /**
     * 특정 팀의 특정 사용자 조회
     */
    Optional<TeamMember> findByTeamAndUser(Team team, User user);
    
    /**
     * 팀의 활성 멤버 수 조회
     */
    int countByTeamAndStatus(Team team, TeamMemberStatus status);
}
