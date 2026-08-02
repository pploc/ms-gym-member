package com.gym.member.member.adapter.out.persistence.entity;

import com.gym.common.persistence.BaseEntity;
import com.gym.member.member.domain.model.MembershipStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
public class SubscriptionEntity extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private String memberId;

    @Column(name = "plan_id", nullable = false)
    private String planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MembershipStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "paused_at")
    private LocalDate pausedAt;

    @Column(name = "remaining_days")
    private Integer remainingDays;

    @Column(name = "pause_count", nullable = false)
    private int pauseCount = 0;
}
