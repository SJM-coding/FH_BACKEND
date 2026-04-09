-- brackets.type 컬럼을 VARCHAR에서 ENUM으로 변경
-- V13에서 VARCHAR(10)으로 생성되었으나 Hibernate가 ENUM 타입을 기대하여 스키마 검증 실패
ALTER TABLE brackets
MODIFY COLUMN type ENUM('AUTO', 'MANUAL') NOT NULL DEFAULT 'AUTO';
