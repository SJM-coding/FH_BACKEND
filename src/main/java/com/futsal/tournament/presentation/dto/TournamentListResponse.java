package com.futsal.tournament.presentation.dto;

import com.futsal.tournament.domain.Gender;
import com.futsal.tournament.domain.PlayerType;
import com.futsal.user.domain.VerificationStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class TournamentListResponse {

    private Long id;
    private String title;
    private LocalDate tournamentDate;
    private String location;
    private String recruitmentStatus;
    private String posterUrl; // 첫 번째 포스터만
    private String registeredByName;
    private String registeredByProfileImage;
    private Gender gender;
    private PlayerType playerType;
    private Boolean isExternal;
    private Boolean organizerVerified; // 인증된 개최자 여부

    public TournamentListResponse(Long id, String title, LocalDate tournamentDate, String location,
                                   String recruitmentStatus, String posterUrl, String registeredByName,
                                   String registeredByProfileImage, Gender gender, PlayerType playerType,
                                   Boolean isExternal, VerificationStatus verificationStatus) {
        this.id = id;
        this.title = title;
        this.tournamentDate = tournamentDate;
        this.location = location;
        this.recruitmentStatus = recruitmentStatus;
        this.posterUrl = posterUrl;
        this.registeredByName = registeredByName;
        this.registeredByProfileImage = registeredByProfileImage;
        this.gender = gender;
        this.playerType = playerType;
        this.isExternal = isExternal;
        this.organizerVerified = verificationStatus == VerificationStatus.VERIFIED;
    }
}
