package com.gym.member.adapter.out.persistence.entity;

import com.gym.common.persistence.BaseEntity;
import com.gym.member.domain.model.PlanType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "membership_plans")
@Getter
@Setter
public class MembershipPlanEntity extends BaseEntity {

    @Column(name = "gym_id", nullable = false)
    private String gymId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false)
    private PlanType planType;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "price_vnd", nullable = false)
    private long priceVnd;

    @Column(name = "description")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
