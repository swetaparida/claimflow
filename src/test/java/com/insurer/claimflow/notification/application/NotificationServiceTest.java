package com.insurer.claimflow.notification.application;

import com.insurer.claimflow.notification.application.port.in.HandleClaimEventUseCase.ClaimEventNotice;
import com.insurer.claimflow.notification.application.port.out.ClaimContactPort;
import com.insurer.claimflow.notification.application.port.out.NotificationSenderPort;
import com.insurer.claimflow.notification.application.port.out.ProcessedEventPort;
import com.insurer.claimflow.notification.domain.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    ProcessedEventPort processedEvents;
    @Mock
    NotificationSenderPort sender;
    @Mock
    ClaimContactPort contacts;

    NotificationService service;
    final UUID claimId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NotificationService(processedEvents, sender, contacts);
    }

    private ClaimEventNotice notice(String type, Map<String, String> attrs) {
        UUID eventId = UUID.randomUUID();
        when(processedEvents.markProcessed(eventId, NotificationService.CONSUMER_NAME)).thenReturn(true);
        return new ClaimEventNotice(eventId, type, claimId, attrs);
    }

    @Test
    void claimCreatedEmailsClaimant() {
        service.handle(notice("ClaimCreated",
                Map.of("claimNumber", "CLM-1", "claimantEmail", "jane@example.com", "claimantName", "Jane")));

        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().channel()).isEqualTo(Notification.Channel.EMAIL);
        assertThat(sent.getValue().recipient()).isEqualTo("jane@example.com");
        assertThat(sent.getValue().subject()).contains("CLM-1");
    }

    @Test
    void claimAssignedNotifiesOfficerInternally() {
        service.handle(notice("ClaimAssigned", Map.of("claimNumber", "CLM-1", "officerId", "officer-1",
                "actor", "supervisor")));

        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().channel()).isEqualTo(Notification.Channel.INTERNAL);
        assertThat(sent.getValue().recipient()).isEqualTo("officer-1");
    }

    @Test
    void decisionLooksUpClaimantContact() {
        when(contacts.claimantEmail(claimId)).thenReturn(Optional.of("jane@example.com"));

        service.handle(notice("DecisionMade", Map.of("claimNumber", "CLM-1", "decision", "APPROVED",
                "approvedAmount", "100.00", "currency", "EUR")));

        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().body()).contains("approved for 100.00 EUR");
    }

    @Test
    void irrelevantStatusChangeSendsNothing() {
        service.handle(notice("ClaimStatusChanged", Map.of("claimNumber", "CLM-1", "toStatus", "UNDER_REVIEW")));

        verify(sender, never()).send(any());
    }

    @Test
    void duplicateEventIsIgnored() {
        UUID eventId = UUID.randomUUID();
        when(processedEvents.markProcessed(eventId, NotificationService.CONSUMER_NAME)).thenReturn(false);

        service.handle(new ClaimEventNotice(eventId, "ClaimCreated", claimId,
                Map.of("claimNumber", "CLM-1", "claimantEmail", "jane@example.com")));

        verify(sender, never()).send(any());
    }
}
