package com.insurer.claimflow.shared.messaging;

import java.util.List;

/**
 * Versioned Kafka topic names. A breaking payload change requires a new topic version.
 */
public final class Topics {

    public static final String CLAIM_CREATED = "claims.claim-created.v1";
    public static final String CLAIM_ASSIGNED = "claims.claim-assigned.v1";
    public static final String STATUS_CHANGED = "claims.status-changed.v1";
    public static final String RESERVE_CHANGED = "claims.reserve-changed.v1";
    public static final String DECISION_MADE = "claims.decision-made.v1";

    public static final List<String> ALL = List.of(CLAIM_CREATED, CLAIM_ASSIGNED, STATUS_CHANGED, RESERVE_CHANGED, DECISION_MADE);

    public static final String HEADER_EVENT_ID = "eventId";
    public static final String HEADER_EVENT_TYPE = "eventType";
    public static final String HEADER_AGGREGATE_ID = "aggregateId";

    private Topics() {
    }
}
