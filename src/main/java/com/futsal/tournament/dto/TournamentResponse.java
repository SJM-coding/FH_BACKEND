package com.futsal.tournament.dto;

import com.futsal.tournament.domain.Gender;
import com.futsal.tournament.domain.PlayerType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TournamentResponse {

    private Long id;
    private String title;
    private LocalDate tournamentDate;
    private String location;
    private PlayerType playerType;
    private Gender gender;
    private String description;
    private int viewCount;
    private String originalLink;
    private String tournamentType;
    private Integer maxTeams;
    private Integer groupCount;
    private Integer teamsPerGroup;
    private Integer advanceCount;  // 조별리그: 각 조당 결선 진출 팀 수
    private Integer swissRounds;
    private Boolean bracketGenerated;
    private String bracketType; // AUTO, MANUAL
    private List<String> bracketImageUrls; // 수동 대진표 이미지
    private List<String> posterUrls; // 포스터 URL 목록
    private String recruitmentStatus; // OPEN, CLOSED
    private Long registeredById;
    private String registeredByName;
    private String registeredByRole; // ORGANIZER, ADMIN
    private Boolean organizerVerified; // 인증된 개최자 여부
    private LocalDateTime createdAt;
    private Boolean isExternal;
    private String externalUrl;
    private String participantCode;  // 참가 신청용 코드
    private String staffCode;        // 운영진 접근용 코드
}
