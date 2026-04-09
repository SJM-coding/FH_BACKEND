package com.futsal.team.event;

/**
 * 팀 프로필(이름·로고) 변경 이벤트
 * Tournament BC에서 구독해 역정규화 필드를 동기화한다.
 */
public record TeamProfileChangedEvent(
    Long teamId,
    String name,
    String logoUrl
) {}
