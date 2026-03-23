package com.futsal.team.repository;

import com.futsal.team.domain.Team;
import com.futsal.team.domain.TeamMember;
import com.futsal.user.domain.User;
import com.futsal.team.domain.TeamMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * 팀 활성 멤버 + 사용자 함께 조회 (N+1 방지)
     */
    @Query("""
        SELECT tm FROM TeamMember tm
        JOIN FETCH tm.user
        WHERE tm.team.id = :teamId AND tm.status = :status
    """)
    List<TeamMember> findByTeamIdAndStatusWithUser(
        @Param("teamId") Long teamId,
        @Param("status") TeamMemberStatus status
    );
    
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

    /**
     * 사용자가 속한 팀 조회 (Team JOIN FETCH로 N+1 방지)
     */
    @Query("""
        SELECT tm FROM TeamMember tm
        JOIN FETCH tm.team t
        WHERE tm.user = :user AND tm.status = :status
    """)
    List<TeamMember> findByUserAndStatusWithTeam(@Param("user") User user, @Param("status") TeamMemberStatus status);

    /**
     * 여러 팀의 활성 멤버 수를 한 번에 조회 (N+1 방지)
     */
    @Query("""
        SELECT tm.team.id, COUNT(tm)
        FROM TeamMember tm
        WHERE tm.team.id IN :teamIds AND tm.status = :status
        GROUP BY tm.team.id
    """)
    List<Object[]> countByTeamIdsAndStatus(@Param("teamIds") List<Long> teamIds, @Param("status") TeamMemberStatus status);
}
