package com.gym.member.member.adapter.out.persistence.entity;

import com.gym.common.persistence.BaseEntity;
import com.gym.member.member.domain.model.PlanType;
import com.gym.member.member.domain.model.PurchaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "pending_purchases")
@Getter
@Setter
public class PendingPurchaseEntity extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private String memberId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "gym_id", nullable = false)
    private String gymId;

    @Column(name = "plan_id", nullable = false)
    private String planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type_snapshot", nullable = false)
    private PlanType planTypeSnapshot;

    @Column(name = "duration_days_snapshot")
    private Integer durationDaysSnapshot;

    @Column(name = "price_vnd_snapshot", nullable = false)
    private long priceVndSnapshot;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "payment_id")
    private String paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PurchaseStatus status;
}
