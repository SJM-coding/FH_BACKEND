-- 대진표 이미지 업로드 기능 지원

-- 1. 대진표 유형 컬럼 추가 (AUTO: 자동 생성, MANUAL: 이미지 업로드)
ALTER TABLE tournaments
ADD COLUMN bracket_type VARCHAR(10) NOT NULL DEFAULT 'AUTO';

-- 2. 대진표 이미지 테이블 생성
CREATE TABLE tournament_bracket_images (
    tournament_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    CONSTRAINT fk_bracket_images_tournament
        FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
);

-- 3. 인덱스 추가
CREATE INDEX idx_bracket_images_tournament_id ON tournament_bracket_images(tournament_id);
