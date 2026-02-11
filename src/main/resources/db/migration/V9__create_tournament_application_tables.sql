-- V9: 대회 신청 시스템 테이블 생성

-- 대회 신청서 테이블
CREATE TABLE tournament_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    applicant_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    message VARCHAR(1000),
    reject_reason VARCHAR(1000),
    applied_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    
    CONSTRAINT fk_application_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE,
    CONSTRAINT fk_application_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_application_applicant FOREIGN KEY (applicant_id) REFERENCES users(id) ON DELETE CASCADE,
    
    UNIQUE KEY uk_tournament_team (tournament_id, team_id)
);

-- 신청서 추가 정보 (formData)
CREATE TABLE application_form_data (
    application_id BIGINT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    field_value VARCHAR(1000),
    
    CONSTRAINT fk_form_data_application FOREIGN KEY (application_id) REFERENCES tournament_applications(id) ON DELETE CASCADE,
    PRIMARY KEY (application_id, field_name)
);

-- 대회 신청 양식 필드
CREATE TABLE tournament_application_form_fields (
    tournament_id BIGINT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    field_label VARCHAR(200) NOT NULL,
    field_type VARCHAR(20) NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    field_options VARCHAR(1000),
    field_placeholder VARCHAR(500),
    
    CONSTRAINT fk_form_field_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
);

-- 인덱스 생성
CREATE INDEX idx_application_tournament ON tournament_applications(tournament_id);
CREATE INDEX idx_application_team ON tournament_applications(team_id);
CREATE INDEX idx_application_applicant ON tournament_applications(applicant_id);
CREATE INDEX idx_application_status ON tournament_applications(status);
CREATE INDEX idx_application_applied_at ON tournament_applications(applied_at);
