-- brackets 테이블 생성
-- Bracket Aggregate Root: tournament_id를 PK로 사용 (1:1 공유 PK)
CREATE TABLE brackets (
  tournament_id BIGINT NOT NULL,
  type          VARCHAR(10) NOT NULL DEFAULT 'AUTO',
  generated     TINYINT(1)  NOT NULL DEFAULT 0,
  PRIMARY KEY (tournament_id),
  CONSTRAINT fk_brackets_tournament
    FOREIGN KEY (tournament_id) REFERENCES tournaments (id)
    ON DELETE CASCADE
);

-- bracket_images 테이블 생성
-- Bracket의 @ElementCollection (MANUAL 타입일 때 이미지 URL 목록)
CREATE TABLE bracket_images (
  tournament_id BIGINT       NOT NULL,
  image_url     VARCHAR(500),
  CONSTRAINT fk_bracket_images_bracket
    FOREIGN KEY (tournament_id) REFERENCES brackets (tournament_id)
    ON DELETE CASCADE
);

-- 기존 tournaments 데이터 → brackets 마이그레이션
INSERT INTO brackets (tournament_id, type, generated)
SELECT
  id,
  COALESCE(bracket_type, 'AUTO'),
  COALESCE(bracket_generated, 0)
FROM tournaments;

-- 기존 tournament_bracket_images 데이터 → bracket_images 마이그레이션
INSERT INTO bracket_images (tournament_id, image_url)
SELECT tournament_id, image_url
FROM tournament_bracket_images;
