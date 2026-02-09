-- data.sql (로컬 테스트용 샘플 데이터)
INSERT INTO tournaments (title, tournament_date, location, region, max_participants, original_link, poster_url, created_at, updated_at)
VALUES
('2026 서울 아마추어 풋살 오픈', '2026-03-15', '서울 강남구 테헤란로 체육관', '서울', 32, 'https://example.com/tournament/1', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('부산 봄시즌 풋살 대회', '2026-03-22', '부산 해운대구 해운대체육관', '부산', 24, 'https://example.com/tournament/2', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('대구 아마추어 컵 2026', '2026-04-05', '대구 수성구 수성체육관', '대구', 16, 'https://example.com/tournament/3', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('인천 스프링 풋살 페스티벌', '2026-04-12', '인천 연수구 연수체육관', '인천', 40, 'https://example.com/tournament/4', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('수원 지역 풋살 토너먼트', '2026-04-20', '수원 영통구 영통체육관', '수원', 20, 'https://example.com/tournament/5', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
