-- teams 테이블 소프트 삭제 지원
ALTER TABLE teams
    ADD COLUMN deleted_at DATETIME NULL;

CREATE INDEX idx_teams_deleted_at ON teams(deleted_at);
