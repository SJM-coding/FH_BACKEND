-- TournamentMatch: @ManyToOne Team 직접참조 제거, 팀 정보 역정규화
-- team1_id/team2_id/winner_id는 기존 FK 컬럼 재활용 (참조 제약만 제거)
-- 팀 이름·로고를 스냅샷으로 저장해 BC 간 직접참조를 끊음

ALTER TABLE tournament_matches
    ADD COLUMN IF NOT EXISTS team1_name   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS team1_logo_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS team2_name   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS team2_logo_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS winner_name  VARCHAR(100);

-- 기존 데이터: teams 테이블에서 이름·로고 역정규화
UPDATE tournament_matches m
    JOIN teams t1 ON t1.id = m.team1_id
    SET m.team1_name    = t1.name,
        m.team1_logo_url = t1.logo_url
WHERE m.team1_id IS NOT NULL;

UPDATE tournament_matches m
    JOIN teams t2 ON t2.id = m.team2_id
    SET m.team2_name    = t2.name,
        m.team2_logo_url = t2.logo_url
WHERE m.team2_id IS NOT NULL;

UPDATE tournament_matches m
    JOIN teams w ON w.id = m.winner_id
    SET m.winner_name = w.name
WHERE m.winner_id IS NOT NULL;

-- FK 제약 제거 (ID 참조로만 사용, JPA 연관관계 없음)
ALTER TABLE tournament_matches
    DROP FOREIGN KEY IF EXISTS fk_match_team1,
    DROP FOREIGN KEY IF EXISTS fk_match_team2,
    DROP FOREIGN KEY IF EXISTS fk_match_winner;
