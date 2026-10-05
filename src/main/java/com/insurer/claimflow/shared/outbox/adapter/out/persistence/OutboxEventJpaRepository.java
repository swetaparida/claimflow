package com.insurer.claimflow.shared.outbox.adapter.out.persistence;

import com.insurer.claimflow.shared.outbox.domain.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    /**
     * Locks the next batch of pending events. {@code SKIP LOCKED} lets several application
     * instances relay concurrently without publishing the same event twice.
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE status = 'PENDING'
            ORDER BY seq
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventJpaEntity> lockNextPendingBatch(@Param("batchSize") int batchSize);

    long countByStatus(OutboxStatus status);

    List<OutboxEventJpaEntity> findByAggregateIdOrderBySeq(UUID aggregateId);

    @Modifying
    @Query("DELETE FROM OutboxEventJpaEntity e WHERE e.status = :status AND e.publishedAt < :before")
    int deleteByStatusAndPublishedAtBefore(@Param("status") OutboxStatus status, @Param("before") Instant before);
}
