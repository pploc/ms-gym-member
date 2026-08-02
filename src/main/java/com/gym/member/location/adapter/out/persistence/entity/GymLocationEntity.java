package com.gym.member.location.adapter.out.persistence.entity;

import com.gym.common.persistence.BaseEntity;
import com.gym.member.location.domain.model.GymLocationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "gym_locations")
@Getter
@Setter
public class GymLocationEntity extends BaseEntity {

    @Column(name = "chain_id", nullable = false)
    private String chainId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private GymLocationStatus status = GymLocationStatus.ACTIVE;
}
