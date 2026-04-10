-- brackets 테이블: 분리 토너먼트 설정 컬럼 추가
ALTER TABLE brackets
    ADD COLUMN knockout_type VARCHAR(10) NOT NULL DEFAULT 'SINGLE',
    ADD COLUMN split_count   INT         NULL;

-- tournament_matches 테이블: 분리 토너먼트 구분 컬럼 추가
ALTER TABLE tournament_matches
    ADD COLUMN bracket_phase VARCHAR(10) NULL;

-- tournaments 테이블: brackets로 이전된 컬럼 제거
-- (데이터는 V13 마이그레이션 시 이미 brackets 테이블로 복사되었고
--  이후 dual write로 동기화 유지됨. 현재 대진표 생성 데이터 없음 확인 후 제거)
ALTER TABLE tournaments
    DROP COLUMN bracket_generated,
    DROP COLUMN bracket_type;

-- tournament_bracket_images 테이블 제거
-- (데이터는 V13 마이그레이션 시 이미 bracket_images 테이블로 복사됨)
DROP TABLE IF EXISTS tournament_bracket_images;
