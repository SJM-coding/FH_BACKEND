-- TournamentParticipant: (tournament_id, team_id) 중복 참가 방지
-- 동일 팀이 같은 대회에 두 번 등록되는 것을 DB 레벨에서 차단한다.
ALTER TABLE tournament_participants
    ADD CONSTRAINT uk_participant_tournament_team
        UNIQUE (tournament_id, team_id);
