package com.insurer.claimflow.notification.application.port.in;

import java.util.Map;
import java.util.UUID;

public interface HandleClaimEventUseCase {

    void handle(ClaimEventNotice notice);

    /**
     * Transport-neutral view of a claim integration event.
     *
     * @param attributes flat event payload (field name → textual value)
     */
    record ClaimEventNotice(UUID eventId, String eventType, UUID claimId, Map<String, String> attributes) {

        public String attr(String name) {
            return attributes.get(name);
        }
    }
}
