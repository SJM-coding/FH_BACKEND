-- V19: SPLIT_STAGE 대회 유형 추가 및 TournamentStructure VO 반영
--
-- 변경 내용:
--   1. tournaments.tournament_type ENUM에 SPLIT_STAGE 값 추가
--   2. brackets 테이블에서 knockout_type, split_count 컬럼 제거
--      (분리 토너먼트 설정이 Tournament.structure.tournamentType = SPLIT_STAGE로 이전됨)

-- 1. tournaments.tournament_type에 SPLIT_STAGE 추가
ALTER TABLE tournaments
    MODIFY COLUMN tournament_type
        ENUM('SINGLE_ELIMINATION','GROUP_STAGE','SPLIT_STAGE','SWISS_SYSTEM','EXTERNAL')
        NOT NULL DEFAULT 'SINGLE_ELIMINATION';

-- 2. brackets 테이블에서 분리 토너먼트 전용 컬럼 제거
--    (분리 토너먼트 여부는 tournament_type = SPLIT_STAGE로 판단)
ALTER TABLE brackets
    DROP COLUMN knockout_type,
    DROP COLUMN split_count;
