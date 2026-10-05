package com.insurer.claimflow.audit.application.port.in;

import com.insurer.claimflow.audit.domain.ClaimHistoryEntry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public API of the Audit module.
 */
public interface AuditTrailUseCase {

    /** Appends an entry; must be called inside the business transaction that caused it. */
    void record(RecordHistoryCommand command);

    List<ClaimHistoryEntry> historyOf(UUID claimId);

    /**
     * @param details any object; serialised to JSON for the audit record
     */
    record RecordHistoryCommand(UUID eventId, UUID claimId, String eventType, String fromStatus, String toStatus,
                                String actor, Object details, Instant occurredAt) {
    }
}
