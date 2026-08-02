package com.gym.member.location.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "gym_qr_secrets")
@Getter
@Setter
public class GymQRSecretEntity {

    @Id
    @Column(name = "gym_id", nullable = false)
    private String gymId;

    @Column(name = "daily_secret", nullable = false)
    private String dailySecret;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
