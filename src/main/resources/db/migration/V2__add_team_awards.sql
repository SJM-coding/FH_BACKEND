-- Add team_awards table for team award history

CREATE TABLE team_awards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    tournament_id BIGINT,
    tournament_name VARCHAR(100),
    award_type VARCHAR(20) NOT NULL,
    award_date DATE NOT NULL,
    description VARCHAR(200),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_team_awards_team
        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_team_awards_team_id
    ON team_awards(team_id);
