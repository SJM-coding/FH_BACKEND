-- 대회 참가 시점의 팀원 스냅샷 테이블
-- 참가 신청 당시 ACTIVE 멤버를 기록해 개인 이력/결과 전파에 활용
CREATE TABLE tournament_participant_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_participant_id BIGINT NOT NULL,
    tournament_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    snapshotted_at DATETIME NOT NULL,
    CONSTRAINT uk_participant_member
        UNIQUE (tournament_participant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_tpm_tournament_id ON tournament_participant_members(tournament_id);
CREATE INDEX idx_tpm_team_id ON tournament_participant_members(team_id);
CREATE INDEX idx_tpm_user_id ON tournament_participant_members(user_id);

-- 개인 수상 뱃지 (스냅샷 기반)
CREATE TABLE user_awards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    team_name VARCHAR(100) NOT NULL,
    organizer_user_id BIGINT NOT NULL,
    organizer_name VARCHAR(50) NOT NULL,
    tournament_id BIGINT NOT NULL,
    tournament_name VARCHAR(200) NOT NULL,
    award_type VARCHAR(20) NOT NULL,
    award_date DATE NOT NULL,
    earned_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_user_awards_user_id ON user_awards(user_id);
CREATE INDEX idx_user_awards_tournament_id ON user_awards(tournament_id);
