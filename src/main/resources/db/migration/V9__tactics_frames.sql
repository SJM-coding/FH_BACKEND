-- 전술 애니메이션 프레임 컬럼 추가
ALTER TABLE team_tactics
ADD COLUMN frames_json TEXT NULL;
