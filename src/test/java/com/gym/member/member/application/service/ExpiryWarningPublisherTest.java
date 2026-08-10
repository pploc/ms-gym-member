package com.gym.member.member.application.service;

import com.gym.member.member.domain.constant.MemberEventTopics;
import com.gym.member.shared.outbox.repository.OutboxEventJpaRepository;
import com.gym.member.shared.outbox.service.OutboxEventWriter;
import com.gym.proto.events.v1.MembershipExpiringSoonEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiryWarningPublisherTest {

    @Mock
    private OutboxEventJpaRepository repository;

    @Mock
    private OutboxEventWriter writer;

    @Test
    void given_existing_dedupe_key_when_publish_then_skips_duplicate_event() {
        // given
        ExpiryWarningPublisher publisher = new ExpiryWarningPublisher(repository, writer);
        MembershipExpiringSoonEvent event = MembershipExpiringSoonEvent.getDefaultInstance();
        when(repository.existsByDedupeKey("warning-1")).thenReturn(true);

        // when
        publisher.publish("member-1", event, "warning-1");

        // then
        verify(writer, never()).write(
                MemberEventTopics.AGGREGATE_TYPE_MEMBER,
                "member-1",
                MemberEventTopics.MEMBERSHIP_EXPIRING_SOON,
                event,
                "warning-1");
        verify(repository, never()).flush();
    }

    @Test
    void given_new_dedupe_key_when_publish_then_writes_and_flushes_event() {
        // given
        ExpiryWarningPublisher publisher = new ExpiryWarningPublisher(repository, writer);
        MembershipExpiringSoonEvent event = MembershipExpiringSoonEvent.getDefaultInstance();
        when(repository.existsByDedupeKey("warning-1")).thenReturn(false);

        // when
        publisher.publish("member-1", event, "warning-1");

        // then
        verify(writer).write(
                MemberEventTopics.AGGREGATE_TYPE_MEMBER,
                "member-1",
                MemberEventTopics.MEMBERSHIP_EXPIRING_SOON,
                event,
                "warning-1");
        verify(repository).flush();
    }
}
