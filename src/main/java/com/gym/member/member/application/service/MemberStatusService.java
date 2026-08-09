package com.gym.member.member.application.service;

import com.gym.member.member.adapter.out.persistence.entity.MemberEntity;
import com.gym.member.member.adapter.out.persistence.repository.MemberJpaRepository;
import com.gym.member.member.adapter.out.persistence.repository.SubscriptionJpaRepository;
import com.gym.member.member.adapter.out.persistence.specification.SubscriptionSpecifications;
import com.gym.member.member.domain.model.MembershipStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberStatusService {

    private final MemberJpaRepository memberRepository;
    private final SubscriptionJpaRepository subscriptionRepository;

    public void refresh(MemberEntity member) {
        MembershipStatus status = subscriptionRepository
                .findAll(SubscriptionSpecifications.hasMemberId(member.getId()))
                .stream()
                .map(subscription -> subscription.getStatus())
                .reduce(MembershipStatus.NONE, MemberStatusService::higherPriority);
        member.setStatus(status);
        memberRepository.save(member);
    }

    private static MembershipStatus higherPriority(MembershipStatus left, MembershipStatus right) {
        return priority(right) > priority(left) ? right : left;
    }

    private static int priority(MembershipStatus status) {
        return switch (status) {
            case ACTIVE -> 3;
            case PAUSED -> 2;
            case EXPIRED -> 1;
            case NONE -> 0;
        };
    }
}
