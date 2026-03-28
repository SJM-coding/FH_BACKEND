-- 개최자 인증 기능 추가

-- 인증 상태 컬럼 추가
ALTER TABLE users
ADD COLUMN verification_status ENUM('NONE', 'PENDING', 'VERIFIED', 'REJECTED') NOT NULL DEFAULT 'NONE';

-- 인증 완료 일시 컬럼 추가
ALTER TABLE users
ADD COLUMN verified_at DATETIME NULL;

-- 인증 상태 인덱스 (PENDING 조회용)
CREATE INDEX idx_users_verification_status ON users(verification_status);
