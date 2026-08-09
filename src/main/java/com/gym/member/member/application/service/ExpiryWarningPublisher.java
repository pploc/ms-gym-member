package com.gym.member.member.application.service;

import com.gym.member.member.domain.constant.MemberEventTopics;
import com.gym.member.shared.outbox.repository.OutboxEventJpaRepository;
import com.gym.member.shared.outbox.service.OutboxEventWriter;
import com.gym.proto.events.v1.MembershipExpiringSoonEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpiryWarningPublisher {

    private final OutboxEventJpaRepository repository;
    private final OutboxEventWriter writer;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(String memberId, MembershipExpiringSoonEvent event, String dedupeKey) {
        if (repository.existsByDedupeKey(dedupeKey)) {
            return;
        }
        writer.write(
                MemberEventTopics.AGGREGATE_TYPE_MEMBER,
                memberId,
                MemberEventTopics.MEMBERSHIP_EXPIRING_SOON,
                event,
                dedupeKey);
        repository.flush();
    }
}
