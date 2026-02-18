-- data.sql (로컬 테스트용 샘플 데이터)
INSERT INTO tournaments (
    title, tournament_date, location, player_type, gender, description,
    view_count, original_link, tournament_type, max_teams, group_count,
    teams_per_group, swiss_rounds, bracket_generated, recruitment_status,
    user_id, created_at, updated_at
)
VALUES
('2026 서울 아마추어 풋살 오픈', '2026-03-15', '서울 강남구 테헤란로 체육관', '비선출', '남자', '서울 지역 아마추어 대회', 0, 'https://example.com/tournament/1', 'SINGLE_ELIMINATION', 32, NULL, NULL, NULL, FALSE, 'OPEN', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('부산 봄시즌 풋살 대회', '2026-03-22', '부산 해운대구 해운대체육관', '비선출', '남자', '봄 시즌 지역 대회', 0, 'https://example.com/tournament/2', 'GROUP_STAGE', 16, 4, 4, NULL, FALSE, 'OPEN', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('대구 아마추어 컵 2026', '2026-04-05', '대구 수성구 수성체육관', '비선출', '남자', '대구 컵 대회', 0, 'https://example.com/tournament/3', 'SINGLE_ELIMINATION', 16, NULL, NULL, NULL, FALSE, 'OPEN', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('인천 스프링 풋살 페스티벌', '2026-04-12', '인천 연수구 연수체육관', '비선출', '남자', '인천 봄 페스티벌', 0, 'https://example.com/tournament/4', 'SWISS_SYSTEM', 12, NULL, NULL, 4, FALSE, 'OPEN', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('수원 지역 풋살 토너먼트', '2026-04-20', '수원 영통구 영통체육관', '비선출', '남자', '수원 지역 토너먼트', 0, 'https://example.com/tournament/5', 'SINGLE_ELIMINATION', 20, NULL, NULL, NULL, FALSE, 'OPEN', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO tournament_posters (tournament_id, poster_url)
VALUES
(1, 'https://example.com/tournament/1/poster1.jpg'),
(2, 'https://example.com/tournament/2/poster1.jpg'),
(3, 'https://example.com/tournament/3/poster1.jpg'),
(4, 'https://example.com/tournament/4/poster1.jpg'),
(5, 'https://example.com/tournament/5/poster1.jpg');
