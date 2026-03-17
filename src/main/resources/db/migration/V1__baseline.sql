-- Baseline schema (clean slate before first deployment)

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    kakao_id BIGINT NOT NULL UNIQUE,
    nickname VARCHAR(50) NOT NULL,
    profile_image_url VARCHAR(500),
    role VARCHAR(20) NOT NULL,
    role_selected BOOLEAN NOT NULL DEFAULT FALSE,
    custom_profile_image BOOLEAN NOT NULL DEFAULT FALSE,
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
    tournament_type VARCHAR(30) NOT NULL DEFAULT 'SINGLE_ELIMINATION',
    max_teams INT NOT NULL DEFAULT 16,
    participant_code VARCHAR(8) UNIQUE,
    staff_code VARCHAR(8) UNIQUE,
    allow_join BOOLEAN NOT NULL DEFAULT TRUE,
    is_external BOOLEAN NOT NULL DEFAULT FALSE,
    external_url VARCHAR(500),
    group_count INT,
    teams_per_group INT,
    swiss_rounds INT,
    bracket_generated BOOLEAN NOT NULL DEFAULT FALSE,
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

CREATE TABLE tournament_groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_id BIGINT NOT NULL,
    group_name VARCHAR(10) NOT NULL,
    group_order INT NOT NULL,
    CONSTRAINT fk_tournament_groups_tournament
        FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tournament_group_teams (
    group_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    PRIMARY KEY (group_id, team_id),
    CONSTRAINT fk_tournament_group_teams_group
        FOREIGN KEY (group_id) REFERENCES tournament_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_tournament_group_teams_team
        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tournament_matches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_id BIGINT NOT NULL,
    round INT NOT NULL,
    match_number INT NOT NULL,
    group_id VARCHAR(10),
    team1_id BIGINT,
    team2_id BIGINT,
    team1score INT,
    team2score INT,
    team1penalty_score INT,
    team2penalty_score INT,
    winner_id BIGINT,
    status VARCHAR(20) NOT NULL,
    scheduled_at DATETIME,
    started_at DATETIME,
    finished_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_tournament_matches_tournament
        FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE,
    CONSTRAINT fk_tournament_matches_team1
        FOREIGN KEY (team1_id) REFERENCES teams(id) ON DELETE SET NULL,
    CONSTRAINT fk_tournament_matches_team2
        FOREIGN KEY (team2_id) REFERENCES teams(id) ON DELETE SET NULL,
    CONSTRAINT fk_tournament_matches_winner
        FOREIGN KEY (winner_id) REFERENCES teams(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_tournament_matches_tournament_id
    ON tournament_matches(tournament_id);

CREATE INDEX idx_tournament_matches_round
    ON tournament_matches(tournament_id, round);

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

CREATE INDEX idx_team_awards_team_id ON team_awards(team_id);

CREATE TABLE tournament_participants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    team_name VARCHAR(100) NOT NULL,
    team_logo_url VARCHAR(500),
    registered_by BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_tournament_participants_tournament
        FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_tournament_participants_tournament_id ON tournament_participants(tournament_id);
CREATE INDEX idx_tournament_participants_team_id ON tournament_participants(team_id);

CREATE TABLE tournament_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    team_name VARCHAR(100) NOT NULL,
    `rank` INT NOT NULL,
    award_type VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_tournament_results_tournament
        FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE,
    CONSTRAINT uq_tournament_results_team UNIQUE (tournament_id, team_id),
    CONSTRAINT uq_tournament_results_rank UNIQUE (tournament_id, `rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_tournament_results_tournament_id ON tournament_results(tournament_id);
