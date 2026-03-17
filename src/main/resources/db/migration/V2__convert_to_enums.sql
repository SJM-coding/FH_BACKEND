-- Convert VARCHAR columns to ENUM types for Hibernate 6 compatibility

-- users.role
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

-- team_awards.award_type
ALTER TABLE team_awards
    MODIFY COLUMN award_type ENUM('CHAMPION','RUNNER_UP','THIRD_PLACE','FOURTH_PLACE','PARTICIPATION') NOT NULL;
