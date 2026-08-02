package com.gym.member.adapter.out.persistence.repository;

import com.gym.member.adapter.out.persistence.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, String> {

    @Modifying
    @Query(value = "INSERT INTO processed_events (event_id, event_type, processed_at) SELECT :eventId, :eventType, :processedAt WHERE NOT EXISTS (SELECT 1 FROM processed_events WHERE event_id = :eventId)", nativeQuery = true)
    int insertIfNotExists(@Param("eventId") String eventId, @Param("eventType") String eventType, @Param("processedAt") Instant processedAt);
}
