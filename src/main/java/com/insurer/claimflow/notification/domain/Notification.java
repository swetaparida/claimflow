package com.insurer.claimflow.notification.domain;

import java.util.Objects;
import java.util.UUID;

public record Notification(
        UUID sourceEventId,
        UUID claimId,
        Channel channel,
        String recipient,
        String subject,
        String body) {

    public enum Channel {
        EMAIL,
        /** In-app work-queue notification for staff. */
        INTERNAL
    }

    public Notification {
        Objects.requireNonNull(sourceEventId);
        Objects.requireNonNull(channel);
        Objects.requireNonNull(recipient);
        Objects.requireNonNull(subject);
    }
}
