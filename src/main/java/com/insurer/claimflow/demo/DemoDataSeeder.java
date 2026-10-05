package com.insurer.claimflow.demo;

import com.insurer.claimflow.assessment.application.port.in.RecordAssessmentUseCase;
import com.insurer.claimflow.assessment.application.port.in.RecordAssessmentUseCase.RecordAssessmentCommand;
import com.insurer.claimflow.assessment.domain.AssessmentOutcome;
import com.insurer.claimflow.intake.application.port.in.SubmitClaimUseCase;
import com.insurer.claimflow.intake.application.port.in.SubmitClaimUseCase.SubmitClaimCommand;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimDecisionUseCase;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimDecisionUseCase.ApproveClaimCommand;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimDecisionUseCase.RejectClaimCommand;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimDecisionUseCase.SettleClaimCommand;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimQueryUseCase;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimReviewUseCase;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimReviewUseCase.AdjustReserveCommand;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimSearchCriteria;
import com.insurer.claimflow.lifecycle.domain.Claim;
import com.insurer.claimflow.lifecycle.domain.IncidentType;
import com.insurer.claimflow.workmanagement.application.port.in.AssignClaimUseCase;
import com.insurer.claimflow.workmanagement.application.port.in.AssignClaimUseCase.AssignClaimCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Seeds a small, realistic set of demo claims at startup so that the queue, the exposure dashboard and the
 * audit trail are not empty on a fresh database.
 *
 * <p>It drives the same application ports the REST adapters use, so the seeded claims go through the real
 * state machine and produce real domain events, outbox rows and history entries. It is idempotent: if the
 * database already holds any claim, it does nothing.
 *
 * <p>Disable with {@code DEMO_SEED_ENABLED=false}.
 */
@Configuration
@ConditionalOnProperty(prefix = "claimflow.demo-seed", name = "enabled", havingValue = "true", matchIfMissing = true)
class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String SEEDER = "demo-seeder";

    @Bean
    ApplicationRunner seedDemoClaims(SubmitClaimUseCase intake,
                                     AssignClaimUseCase assignment,
                                     RecordAssessmentUseCase assessments,
                                     ClaimReviewUseCase review,
                                     ClaimDecisionUseCase decisions,
                                     ClaimQueryUseCase claims,
                                     Clock clock) {
        return args -> {
            if (claims.search(new ClaimSearchCriteria(null, null, null), 0, 1).totalElements() > 0) {
                log.info("Demo seeding skipped: the database already contains claims");
                return;
            }
            LocalDate today = LocalDate.now(clock);
            log.info("Seeding demo claims ...");

            // 1. Freshly reported, waiting in the unassigned queue.
            submit(intake, "POL-1001", "Jane Doe", "jane.doe@example.com", "+49 30 1234567",
                    IncidentType.AUTO_COLLISION, today.minusDays(1), "Berlin, Alexanderplatz",
                    "Rear-ended at a traffic light; bumper and tailgate damaged.",
                    new BigDecimal("4500.00"), "EUR");

            // 2. Assigned, not yet picked up.
            Claim assigned = submit(intake, "POL-1001", "Jane Doe", "jane.doe@example.com", "+49 30 1234567",
                    IncidentType.THEFT, today.minusDays(6), "Berlin, Mitte",
                    "Catalytic converter stolen from the parked vehicle overnight.",
                    new BigDecimal("1800.00"), "EUR");
            assignment.assign(new AssignClaimCommand(assigned.getId(), "officer-1", "supervisor-1",
                    "Motor team, standard priority"));

            // 3. Under review, reserve re-estimated by the officer.
            Claim underReview = submit(intake, "POL-1002", "John Smith", "john.smith@example.com", "+49 40 9876543",
                    IncidentType.WATER_DAMAGE, today.minusDays(9), "Hamburg, Altona",
                    "Burst pipe under the kitchen sink; flooring and cabinets affected.",
                    new BigDecimal("12000.00"), "EUR");
            assignment.assign(new AssignClaimCommand(underReview.getId(), "officer-2", "supervisor-1",
                    "Property team"));
            review.beginReview(new ClaimReviewUseCase.BeginReviewCommand(underReview.getId(), "officer-2"));
            review.adjustReserve(new AdjustReserveCommand(underReview.getId(), new BigDecimal("9500.00"),
                    "Loss adjuster estimate received", "officer-2"));

            // 4. Waiting for documents from the claimant.
            Claim infoRequired = submit(intake, "POL-1002", "John Smith", "john.smith@example.com", "+49 40 9876543",
                    IncidentType.FIRE, today.minusDays(14), "Hamburg, Altona",
                    "Kitchen fire caused by a faulty appliance; smoke damage throughout the flat.",
                    new BigDecimal("31000.00"), "EUR");
            assignment.assign(new AssignClaimCommand(infoRequired.getId(), "officer-2", "supervisor-1", null));
            assessments.record(new RecordAssessmentCommand(infoRequired.getId(), "officer-2",
                    AssessmentOutcome.INFO_REQUIRED,
                    "Fire brigade report and the appliance purchase invoice are still missing.",
                    new BigDecimal("25000.00")));

            // 5. Approved, awaiting payment.
            Claim approved = submit(intake, "POL-1001", "Jane Doe", "jane.doe@example.com", "+49 30 1234567",
                    IncidentType.NATURAL_DISASTER, today.minusDays(21), "Potsdam",
                    "Hail damage to the roof and bonnet during a summer storm.",
                    new BigDecimal("6200.00"), "EUR");
            assignment.assign(new AssignClaimCommand(approved.getId(), "officer-1", "supervisor-1", null));
            assessments.record(new RecordAssessmentCommand(approved.getId(), "officer-1",
                    AssessmentOutcome.RECOMMEND_APPROVAL,
                    "Damage consistent with the weather report; two independent repair quotes obtained.",
                    new BigDecimal("5800.00")));
            decisions.approve(new ApproveClaimCommand(approved.getId(), new BigDecimal("5800.00"),
                    "Covered under section 2; policy excess of 400 applied.", "manager-1"));

            // 6. Settled end to end.
            Claim settled = submit(intake, "POL-3001", "Maria Garcia", "maria.garcia@example.com", "+1 212 5550100",
                    IncidentType.HEALTH, today.minusDays(30), "New York, NY",
                    "Emergency dental treatment while travelling.",
                    new BigDecimal("2400.00"), "USD");
            assignment.assign(new AssignClaimCommand(settled.getId(), "officer-3", "supervisor-2", null));
            assessments.record(new RecordAssessmentCommand(settled.getId(), "officer-3",
                    AssessmentOutcome.RECOMMEND_APPROVAL, "Treatment and invoice verified with the clinic.",
                    new BigDecimal("2400.00")));
            decisions.approve(new ApproveClaimCommand(settled.getId(), new BigDecimal("2400.00"),
                    "Fully covered under the health policy.", "manager-2"));
            decisions.settle(new SettleClaimCommand(settled.getId(), new BigDecimal("2400.00"),
                    "PAY-DEMO-0001", "finance-1"));

            // 7. Rejected.
            Claim rejected = submit(intake, "POL-1002", "John Smith", "john.smith@example.com", "+49 40 9876543",
                    IncidentType.LIABILITY, today.minusDays(40), "Hamburg, Altona",
                    "Neighbour claims water ingress from the flat above.",
                    new BigDecimal("4800.00"), "EUR");
            assignment.assign(new AssignClaimCommand(rejected.getId(), "officer-2", "supervisor-1", null));
            assessments.record(new RecordAssessmentCommand(rejected.getId(), "officer-2",
                    AssessmentOutcome.RECOMMEND_REJECTION,
                    "Damage is the result of gradual wear to the sealant, which the policy excludes.",
                    BigDecimal.ZERO));
            decisions.reject(new RejectClaimCommand(rejected.getId(),
                    "Gradual deterioration is excluded under section 4 of the policy.", "manager-1"));

            log.info("Demo seeding complete: 7 claims across REPORTED, ASSIGNED, UNDER_REVIEW, INFO_REQUIRED, "
                    + "APPROVED, SETTLED and REJECTED");
        };
    }

    private static Claim submit(SubmitClaimUseCase intake, String policyNumber, String name, String email,
                                String phone, IncidentType type, LocalDate date, String location,
                                String description, BigDecimal amount, String currency) {
        return intake.submit(new SubmitClaimCommand(policyNumber, name, email, phone, type, date, location,
                description, amount, currency, SEEDER));
    }
}
