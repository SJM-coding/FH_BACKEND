-- Baseline schema (clean slate before first deployment)

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    kakao_id BIGINT NOT NULL UNIQUE,
    nickname VARCHAR(50) NOT NULL,
    profile_image_url VARCHAR(500),
    role VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tournaments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    tournament_date DATE NOT NULL,
    location VARCHAR(100) NOT NULL,
    player_type VARCHAR(20) NOT NULL,
    gender VARCHAR(10) NOT NULL,
    description TEXT NOT NULL,
    view_count INT NOT NULL DEFAULT 0,
    original_link VARCHAR(500) NOT NULL,
    recruitment_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    user_id BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_tournaments_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tournament_posters (
    tournament_id BIGINT NOT NULL,
    poster_url VARCHAR(500) NOT NULL,
    PRIMARY KEY (tournament_id, poster_url),
    CONSTRAINT fk_tournament_posters_tournament
        FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_tournament_posters_tournament_id
    ON tournament_posters(tournament_id);

CREATE TABLE teams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    region VARCHAR(50) NOT NULL,
    logo_url VARCHAR(500),
    captain_user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_teams_captain_user
        FOREIGN KEY (captain_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE team_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    position VARCHAR(20) NOT NULL DEFAULT 'NONE',
    joined_at DATETIME NOT NULL,
    CONSTRAINT fk_team_members_team
        FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT fk_team_members_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_team_members_team_user UNIQUE (team_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_team_members_team_id
    ON team_members(team_id);

CREATE INDEX idx_team_members_user_id
    ON team_members(user_id);

CREATE TABLE team_tactics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    formation VARCHAR(20) NOT NULL,
    players_json TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_team_tactics_team
        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT uq_team_tactics_team UNIQUE (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_team_tactics_team_id
    ON team_tactics(team_id);

CREATE TABLE tournament_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    memo TEXT,
    applied_at DATETIME NOT NULL,
    CONSTRAINT fk_tournament_applications_tournament
        FOREIGN KEY (tournament_id) REFERENCES tournaments(id),
    CONSTRAINT fk_tournament_applications_team
        FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT uq_tournament_applications UNIQUE (tournament_id, team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_tournament_applications_tournament_id
    ON tournament_applications(tournament_id);

CREATE INDEX idx_tournament_applications_team_id
    ON tournament_applications(team_id);
