-- 조별리그에서 각 조당 결선 진출 팀 수
ALTER TABLE tournaments ADD COLUMN advance_count INT DEFAULT 2;
