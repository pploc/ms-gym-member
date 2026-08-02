package com.gym.member.adapter.out.persistence.repository;

import com.gym.member.adapter.out.persistence.entity.OutboxEventEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID>, JpaSpecificationExecutor<OutboxEventEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Query("""
            select e from OutboxEventEntity e
            where (e.status = :pending or (e.status = :inFlight and e.nextAttemptAt <= :now))
            order by e.createdAt asc, e.id asc
            """)
    List<OutboxEventEntity> findReady(
            @Param("now") Instant now,
            @Param("pending") OutboxEventEntity.OutboxStatus pending,
            @Param("inFlight") OutboxEventEntity.OutboxStatus inFlight,
            Pageable pageable
    );

    default List<OutboxEventEntity> findReady(
            Instant now,
            OutboxEventEntity.OutboxStatus pending,
            OutboxEventEntity.OutboxStatus inFlight,
            int batchSize
    ) {
        return findReady(now, pending, inFlight, Pageable.ofSize(batchSize));
    }

    boolean existsByAggregateIdAndEventTypeAndCreatedAtGreaterThanEqual(
            String aggregateId,
            String eventType,
            Instant since
    );
}
