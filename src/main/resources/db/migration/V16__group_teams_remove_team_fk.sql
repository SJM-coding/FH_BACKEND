-- tournament_group_teams: teams(id) FK 제거
-- TournamentGroup을 @ElementCollection으로 전환하면서
-- team BC에 대한 직접 FK 의존성을 제거한다.
ALTER TABLE tournament_group_teams
    DROP FOREIGN KEY fk_tournament_group_teams_team;
