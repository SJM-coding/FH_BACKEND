-- data.sql (로컬 테스트용 샘플 데이터)
INSERT INTO tournaments (
    title, tournament_date, location, player_type, gender, description,
    view_count, original_link, tournament_type, max_teams, group_count,
    teams_per_group, swiss_rounds, bracket_generated, recruitment_status,
    share_code, allow_join, is_external, external_url, user_id, created_at, updated_at
)
VALUES
('2026 서울 아마추어 풋살 오픈', '2026-03-15', '서울 강남구 테헤란로 체육관', '비선출', '남자', '서울 지역 아마추어 대회', 0, 'https://example.com/tournament/1', 'SINGLE_ELIMINATION', 32, NULL, NULL, NULL, FALSE, 'OPEN', NULL, TRUE, FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('부산 봄시즌 풋살 대회', '2026-03-22', '부산 해운대구 해운대체육관', '비선출', '남자', '봄 시즌 지역 대회', 0, 'https://example.com/tournament/2', 'GROUP_STAGE', 16, 4, 4, NULL, FALSE, 'OPEN', NULL, TRUE, FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('대구 아마추어 컵 2026', '2026-04-05', '대구 수성구 수성체육관', '비선출', '남자', '대구 컵 대회', 0, 'https://example.com/tournament/3', 'SINGLE_ELIMINATION', 16, NULL, NULL, NULL, FALSE, 'OPEN', NULL, TRUE, FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('인천 스프링 풋살 페스티벌', '2026-04-12', '인천 연수구 연수체육관', '비선출', '남자', '인천 봄 페스티벌', 0, 'https://example.com/tournament/4', 'SWISS_SYSTEM', 12, NULL, NULL, 4, FALSE, 'OPEN', NULL, TRUE, FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('수원 지역 풋살 토너먼트', '2026-04-20', '수원 영통구 영통체육관', '비선출', '남자', '수원 지역 토너먼트', 0, 'https://example.com/tournament/5', 'SINGLE_ELIMINATION', 20, NULL, NULL, NULL, FALSE, 'OPEN', NULL, TRUE, FALSE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO tournament_posters (tournament_id, poster_url)
VALUES
(1, 'https://example.com/tournament/1/poster1.jpg'),
(2, 'https://example.com/tournament/2/poster1.jpg'),
(3, 'https://example.com/tournament/3/poster1.jpg'),
(4, 'https://example.com/tournament/4/poster1.jpg'),
(5, 'https://example.com/tournament/5/poster1.jpg');

-- 대진표 테스트용 데이터
-- 테스트 사용자 (9명)
INSERT INTO users (kakao_id, nickname, profile_image_url, role, created_at, updated_at)
VALUES 
('1000000001', '테스트 주최자', 'https://via.placeholder.com/150', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('1000000002', '테스트 팀장1', 'https://via.placeholder.com/150', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('1000000003', '테스트 팀장2', 'https://via.placeholder.com/150', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('1000000004', '테스트 팀장3', 'https://via.placeholder.com/150', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('1000000005', '테스트 팀장4', 'https://via.placeholder.com/150', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('1000000006', '테스트 팀장5', 'https://via.placeholder.com/150', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('1000000007', '테스트 팀장6', 'https://via.placeholder.com/150', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('1000000008', '테스트 팀장7', 'https://via.placeholder.com/150', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('1000000009', '테스트 팀장8', 'https://via.placeholder.com/150', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 추가 테스트 사용자 (8명)
INSERT INTO users (kakao_id, nickname, profile_image_url, role, created_at, updated_at)
VALUES 
('1000000010', '테스트 팀장9', 'https://via.placeholder.com/150', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('1000000011', '테스트 팀장10', 'https://via.placeholder.com/150', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('1000000012', '테스트 팀장11', 'https://via.placeholder.com/150', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('1000000013', '테스트 팀장12', 'https://via.placeholder.com/150', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('1000000014', '테스트 팀장13', 'https://via.placeholder.com/150', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('1000000015', '테스트 팀장14', 'https://via.placeholder.com/150', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('1000000016', '테스트 팀장15', 'https://via.placeholder.com/150', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('1000000017', '테스트 팀장16', 'https://via.placeholder.com/150', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 테스트 팀 (8개)
INSERT INTO teams (name, logo_url, region, captain_user_id, created_at, updated_at)
VALUES 
('FC 서울', 'https://via.placeholder.com/100', '서울', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('강남 유나이티드', 'https://via.placeholder.com/100', '서울', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('압구정 FC', 'https://via.placeholder.com/100', '서울', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('역삼 FC', 'https://via.placeholder.com/100', '서울', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('청담 FC', 'https://via.placeholder.com/100', '서울', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('논현 FC', 'https://via.placeholder.com/100', '서울', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('삼성 FC', 'https://via.placeholder.com/100', '서울', 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('대치 FC', 'https://via.placeholder.com/100', '서울', 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 테스트 팀 (랜덤 8개 추가)
INSERT INTO teams (name, logo_url, region, captain_user_id, created_at, updated_at)
VALUES 
('블루 스파크 FC', 'https://via.placeholder.com/100', '서울', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('레드 스트라이커즈', 'https://via.placeholder.com/100', '서울', 11, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('나이트 러너스', 'https://via.placeholder.com/100', '서울', 12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('골든 윙즈', 'https://via.placeholder.com/100', '서울', 13, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('실버 타이탄', 'https://via.placeholder.com/100', '서울', 14, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('그린 웨이브', 'https://via.placeholder.com/100', '서울', 15, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('블랙 코멧', 'https://via.placeholder.com/100', '서울', 16, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('화이트 폭스', 'https://via.placeholder.com/100', '서울', 17, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 테스트 대회 생성 (8팀 토너먼트)
INSERT INTO tournaments (
    title, tournament_date, location, player_type, gender, description,
    view_count, original_link, tournament_type, max_teams, bracket_generated,
    recruitment_status, share_code, allow_join, is_external, external_url, user_id, created_at, updated_at
)
VALUES (
    '테스트 토너먼트 (8팀)',
    '2026-06-15',
    '서울 강남구',
    'NON_PRO',
    'MALE',
    '대진표 테스트용 8팀 토너먼트',
    0,
    'https://example.com/test',
    'SINGLE_ELIMINATION',
    8,
    false,
    'OPEN',
    'TEST8888',
    true,
    false,
    NULL,
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 테스트 대회에 8개 팀 참가 신청 (tournament_id=6으로 가정)
INSERT INTO tournament_participants (tournament_id, team_id, team_name, team_logo_url, registered_by, status, created_at, updated_at)
VALUES 
(6, 1, 'FC 서울', 'https://via.placeholder.com/100', 1, 'CONFIRMED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6, 2, '강남 유나이티드', 'https://via.placeholder.com/100', 2, 'CONFIRMED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6, 3, '압구정 FC', 'https://via.placeholder.com/100', 3, 'CONFIRMED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6, 4, '역삼 FC', 'https://via.placeholder.com/100', 4, 'CONFIRMED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6, 5, '청담 FC', 'https://via.placeholder.com/100', 5, 'CONFIRMED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6, 6, '논현 FC', 'https://via.placeholder.com/100', 6, 'CONFIRMED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6, 7, '삼성 FC', 'https://via.placeholder.com/100', 7, 'CONFIRMED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6, 8, '대치 FC', 'https://via.placeholder.com/100', 8, 'CONFIRMED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
