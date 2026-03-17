-- Fix award_type column to use ENUM type

ALTER TABLE team_awards
    MODIFY COLUMN award_type ENUM('CHAMPION','RUNNER_UP','THIRD_PLACE','FOURTH_PLACE','PARTICIPATION') NOT NULL;
