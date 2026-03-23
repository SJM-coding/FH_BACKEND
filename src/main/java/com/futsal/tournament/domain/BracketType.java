package com.futsal.tournament.domain;

/**
 * 대진표 생성 방식
 */
public enum BracketType {

    /**
     * 시스템 자동 생성
     * - 참가팀 선택 → 대진표 자동 매칭
     * - TournamentMatch 테이블에 경기 데이터 저장
     */
    AUTO,

    /**
     * 이미지 직접 업로드
     * - 개최자가 대진표 이미지 업로드
     * - S3에 이미지 저장
     */
    MANUAL
}
