-- Add team_awards table and fix all ENUM columns

-- 1. Create team_awards table with correct ENUM type
CREATE TABLE team_awards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    tournament_id BIGINT,
    tournament_name VARCHAR(100),
    award_type ENUM('CHAMPION','RUNNER_UP','THIRD_PLACE','FOURTH_PLACE','PARTICIPATION') NOT NULL,
    award_date DATE NOT NULL,
    description VARCHAR(200),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_team_awards_team
        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_team_awards_team_id ON team_awards(team_id);

-- 2. Fix all VARCHAR columns that should be ENUM types

-- users.role (기존 USER → PARTICIPANT 변환)
UPDATE users SET role = 'PARTICIPANT' WHERE role = 'USER';
ALTER TABLE users
    MODIFY COLUMN role ENUM('PARTICIPANT','ORGANIZER','ADMIN') NOT NULL;

-- team_members.role
ALTER TABLE team_members
    MODIFY COLUMN role ENUM('CAPTAIN','MEMBER') NOT NULL;

-- team_members.status
ALTER TABLE team_members
    MODIFY COLUMN status ENUM('ACTIVE','INVITED','LEFT','KICKED') NOT NULL;

-- team_members.position
ALTER TABLE team_members
    MODIFY COLUMN position ENUM('GOL','FIXO','PIVO','ALA','NONE') NOT NULL DEFAULT 'NONE';

-- tournaments.tournament_type
ALTER TABLE tournaments
    MODIFY COLUMN tournament_type ENUM('SINGLE_ELIMINATION','GROUP_STAGE','SWISS_SYSTEM') NOT NULL DEFAULT 'SINGLE_ELIMINATION';

-- tournament_matches.status
ALTER TABLE tournament_matches
    MODIFY COLUMN status ENUM('SCHEDULED','IN_PROGRESS','FINISHED','CANCELLED') NOT NULL;
