-- 외부 대회에 EXTERNAL 타입과 maxTeams=0 적용
-- (기존 외부 대회가 있을 경우를 대비한 업데이트)

UPDATE tournaments
SET tournament_type = 'EXTERNAL', max_teams = 0
WHERE is_external = true AND (tournament_type IS NULL OR tournament_type != 'EXTERNAL');
