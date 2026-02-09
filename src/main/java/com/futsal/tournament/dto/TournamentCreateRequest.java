package com.futsal.tournament.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TournamentCreateRequest {

    @NotBlank(message = "대회 이름은 필수입니다")
    private String title;

    @NotNull(message = "대회 날짜는 필수입니다")
    private LocalDate tournamentDate;

    @NotBlank(message = "장소는 필수입니다")
    private String location;

    @NotBlank(message = "선출 타입은 필수입니다")
    private String playerType;

    @NotBlank(message = "성별은 필수입니다")
    private String gender;

    @NotBlank(message = "설명은 필수입니다")
    private String description;

    @NotBlank(message = "원본 링크는 필수입니다")
    private String originalLink;

    private List<String> posterUrls; // S3 업로드 후 URL 목록
}
