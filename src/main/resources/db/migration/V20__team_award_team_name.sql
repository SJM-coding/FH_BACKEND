-- team_awards 테이블에 team_name 컬럼 추가
-- 팀이 삭제되어도 수상 기록의 팀명을 유지하기 위한 비정규화 컬럼
ALTER TABLE team_awards
    ADD COLUMN team_name VARCHAR(100);

-- 기존 레코드는 teams 테이블의 현재 이름으로 채움
UPDATE team_awards ta
    JOIN teams t ON ta.team_id = t.id
SET ta.team_name = t.name
WHERE ta.team_name IS NULL;

ALTER TABLE team_awards
    MODIFY COLUMN team_name VARCHAR(100) NOT NULL;
