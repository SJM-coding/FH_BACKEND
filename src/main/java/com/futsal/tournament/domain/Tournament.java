package com.futsal.tournament.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.futsal.user.domain.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tournaments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private LocalDate tournamentDate;

    @Column(nullable = false, length = 100)
    private String location;

    @Column(nullable = false, length = 20)
    private String playerType; // 비선출/선출

    @Column(nullable = false, length = 10)
    private String gender; // 남자/여자

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int viewCount;

    @Column(nullable = false, length = 500)
    private String originalLink; // 원본 사이트 링크

    @ElementCollection
    @CollectionTable(name = "tournament_posters", joinColumns = @JoinColumn(name = "tournament_id"))
    @Column(name = "poster_url", length = 500)
    @Builder.Default
    private List<String> posterUrls = new ArrayList<>(); // 여러 포스터 URL

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String recruitmentStatus = "OPEN"; // 모집 상태: OPEN(모집중), CLOSED(마감)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User registeredBy; // 등록자

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (posterUrls == null) {
            posterUrls = new ArrayList<>();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 등록자 확인
     */
    public boolean isRegisteredBy(Long userId) {
        return this.registeredBy != null && this.registeredBy.getId().equals(userId);
    }

    /**
     * 포스터 추가
     */
    public void addPosterUrl(String posterUrl) {
        if (posterUrls == null) {
            posterUrls = new ArrayList<>();
        }
        posterUrls.add(posterUrl);
    }

    /**
     * 포스터 목록 설정
     */
    public void setPosterUrls(List<String> posterUrls) {
        this.posterUrls = posterUrls != null ? posterUrls : new ArrayList<>();
    }
}
