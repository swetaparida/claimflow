package com.insurer.claimflow.notification.application;

import com.insurer.claimflow.notification.application.port.in.HandleClaimEventUseCase;
import com.insurer.claimflow.notification.application.port.out.ClaimContactPort;
import com.insurer.claimflow.notification.application.port.out.NotificationSenderPort;
import com.insurer.claimflow.notification.application.port.out.ProcessedEventPort;
import com.insurer.claimflow.notification.domain.Notification;
import com.insurer.claimflow.notification.domain.Notification.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Turns claim events into claimant / officer notifications. Idempotent per event id.
 */
@Service
public class NotificationService implements HandleClaimEventUseCase {

    public static final String CONSUMER_NAME = "notification";

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ProcessedEventPort processedEvents;
    private final NotificationSenderPort sender;
    private final ClaimContactPort contacts;

    public NotificationService(ProcessedEventPort processedEvents, NotificationSenderPort sender,
                               ClaimContactPort contacts) {
        this.processedEvents = processedEvents;
        this.sender = sender;
        this.contacts = contacts;
    }

    @Override
    @Transactional
    public void handle(ClaimEventNotice notice) {
        if (!processedEvents.markProcessed(notice.eventId(), CONSUMER_NAME)) {
            log.debug("Skipping duplicate event {}", notice.eventId());
            return;
        }
        compose(notice).ifPresent(sender::send);
    }

    Optional<Notification> compose(ClaimEventNotice n) {
        String claimNumber = n.attr("claimNumber");
        return switch (n.eventType()) {
            case "ClaimCreated" -> Optional.of(new Notification(n.eventId(), n.claimId(), Channel.EMAIL,
                    n.attr("claimantEmail"),
                    "We received your claim " + claimNumber,
                    "Dear %s, your claim %s has been registered and will be assigned to a claims officer shortly."
                            .formatted(n.attr("claimantName"), claimNumber)));
            case "ClaimAssigned" -> Optional.of(new Notification(n.eventId(), n.claimId(), Channel.INTERNAL,
                    n.attr("officerId"),
                    "Claim " + claimNumber + " assigned to you",
                    "Claim %s has been added to your work queue by %s.".formatted(claimNumber, n.attr("actor"))));
            case "DecisionMade" -> toClaimant(n, "Decision on claim " + claimNumber,
                    "APPROVED".equals(n.attr("decision"))
                            ? "Your claim %s was approved for %s %s.".formatted(claimNumber, n.attr("approvedAmount"),
                            n.attr("currency"))
                            : "Your claim %s was rejected. Reason: %s".formatted(claimNumber, n.attr("reason")));
            case "ClaimStatusChanged" -> switch (String.valueOf(n.attr("toStatus"))) {
                case "INFO_REQUIRED" -> toClaimant(n, "More information needed for claim " + claimNumber,
                        "Please provide additional information: " + n.attr("reason"));
                case "SETTLED" -> toClaimant(n, "Payment issued for claim " + claimNumber,
                        "Your claim %s has been settled. %s".formatted(claimNumber, n.attr("reason")));
                default -> Optional.empty();
            };
            default -> Optional.empty();
        };
    }

    private Optional<Notification> toClaimant(ClaimEventNotice n, String subject, String body) {
        Optional<String> email = contacts.claimantEmail(n.claimId());
        if (email.isEmpty()) {
            log.warn("No claimant contact for claim {}; dropping notification for event {}", n.claimId(), n.eventId());
        }
        return email.map(to -> new Notification(n.eventId(), n.claimId(), Channel.EMAIL, to, subject, body));
    }
}
