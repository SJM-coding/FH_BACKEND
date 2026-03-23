-- 누락된 ENUM 컬럼 수정 (Hibernate 6.2+ 호환)

-- 1. tournaments.gender: VARCHAR → ENUM
ALTER TABLE tournaments
MODIFY COLUMN gender ENUM('MALE', 'FEMALE', 'MIXED') NOT NULL;

-- 2. tournaments.player_type: VARCHAR → ENUM
ALTER TABLE tournaments
MODIFY COLUMN player_type ENUM('NON_PRO', 'PRO') NOT NULL;

-- 3. tournaments.tournament_type: EXTERNAL 추가
ALTER TABLE tournaments
MODIFY COLUMN tournament_type ENUM('SINGLE_ELIMINATION', 'GROUP_STAGE', 'SWISS_SYSTEM', 'EXTERNAL') NOT NULL;
