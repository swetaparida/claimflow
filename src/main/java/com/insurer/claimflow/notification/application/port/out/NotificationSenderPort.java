package com.insurer.claimflow.notification.application.port.out;

import com.insurer.claimflow.notification.domain.Notification;

public interface NotificationSenderPort {

    void send(Notification notification);
}
