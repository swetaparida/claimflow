package com.insurer.claimflow.notification.adapter.out.email;

import com.insurer.claimflow.notification.application.port.out.NotificationSenderPort;
import com.insurer.claimflow.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MVP delivery channel: writes notifications to the log. Replace with an SMTP / messaging-provider adapter
 * implementing {@link NotificationSenderPort} for production delivery.
 */
@Component
class LoggingNotificationSender implements NotificationSenderPort {

    private static final Logger log = LoggerFactory.getLogger("claimflow.notifications");

    @Override
    public void send(Notification n) {
        log.info("[{}] to={} claim={} subject=\"{}\" body=\"{}\"", n.channel(), n.recipient(), n.claimId(),
                n.subject(), n.body());
    }
}
