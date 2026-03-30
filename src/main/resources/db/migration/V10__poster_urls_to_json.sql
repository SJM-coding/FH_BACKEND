-- 포스터 URL을 JSON 컬럼으로 마이그레이션
-- 기존 tournament_posters 테이블 데이터를 tournaments.poster_urls JSON 컬럼으로 이동

-- 1. tournaments 테이블에 JSON 컬럼 추가
ALTER TABLE tournaments ADD COLUMN poster_urls JSON DEFAULT '[]';

-- 2. 기존 데이터 마이그레이션 (tournament_posters -> JSON 배열)
-- 주의: 기존 테이블에 순서 컬럼이 없어서 URL 알파벳 순으로 정렬
UPDATE tournaments t
SET poster_urls = (
    SELECT JSON_ARRAYAGG(tp.poster_url ORDER BY tp.poster_url)
    FROM tournament_posters tp
    WHERE tp.tournament_id = t.id
)
WHERE EXISTS (
    SELECT 1 FROM tournament_posters tp WHERE tp.tournament_id = t.id
);

-- 3. 기존 테이블 삭제
DROP TABLE IF EXISTS tournament_posters;
