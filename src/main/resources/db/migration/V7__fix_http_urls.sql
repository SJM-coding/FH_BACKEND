-- HTTP URL을 HTTPS로 변환 (Mixed Content 방지)

-- 사용자 프로필 이미지
UPDATE users
SET profile_image_url = REPLACE(profile_image_url, 'http://', 'https://')
WHERE profile_image_url LIKE 'http://%';

-- 대회 포스터
UPDATE tournament_posters
SET poster_url = REPLACE(poster_url, 'http://', 'https://')
WHERE poster_url LIKE 'http://%';

-- 대진표 이미지
UPDATE tournament_bracket_images
SET image_url = REPLACE(image_url, 'http://', 'https://')
WHERE image_url LIKE 'http://%';

-- 대회 외부 URL
UPDATE tournaments
SET external_url = REPLACE(external_url, 'http://', 'https://')
WHERE external_url LIKE 'http://%';

-- 대회 원본 링크
UPDATE tournaments
SET original_link = REPLACE(original_link, 'http://', 'https://')
WHERE original_link LIKE 'http://%';
