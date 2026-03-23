-- bracket_type 컬럼을 VARCHAR에서 ENUM으로 변경

ALTER TABLE tournaments
MODIFY COLUMN bracket_type ENUM('AUTO', 'MANUAL') NOT NULL DEFAULT 'AUTO';
