package com.futsal.team.domain;

import com.futsal.user.domain.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "teams")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(length = 500)
    private String logoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "captain_user_id", nullable = false)
    private User captain;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 팀장 확인
     */
    public boolean isCaptain(Long userId) {
        return this.captain != null && this.captain.getId().equals(userId);
    }

    /**
     * 팀 정보 업데이트
     */
    public void updateTeam(String name, String region, String logoUrl) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (region != null && !region.isBlank()) {
            this.region = region;
        }
        if (logoUrl != null) {
            this.logoUrl = logoUrl;
        }
    }
}
