package com.futsal.team.domain;

import com.futsal.team.event.TeamProfileChangedEvent;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.time.LocalDateTime;

@Entity
@Table(name = "teams")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Team extends AbstractAggregateRoot<Team> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(length = 500)
    private String logoUrl;

    @Column(name = "captain_user_id", nullable = false)
    private Long captainUserId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    /**
     * 팀장 확인
     */
    public boolean isCaptain(Long userId) {
        return this.captainUserId != null && this.captainUserId.equals(userId);
    }

    /**
     * 팀 정보 업데이트
     */
    public void updateTeam(String name, String region, String logoUrl) {
        boolean profileChanged = false;

        if (name != null && !name.isBlank()) {
            this.name = name;
            profileChanged = true;
        }
        if (region != null && !region.isBlank()) {
            this.region = region;
        }
        if (logoUrl != null) {
            this.logoUrl = logoUrl;
            profileChanged = true;
        }

        if (profileChanged) {
            registerEvent(new TeamProfileChangedEvent(this.id, this.name, this.logoUrl));
        }
    }
}
