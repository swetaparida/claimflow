-- =====================================================================================
-- ClaimFlow core schema: claims, incidents, assignments, assessments, audit history
-- =====================================================================================

CREATE SEQUENCE claim_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE claim
(
    id                  UUID PRIMARY KEY,
    claim_number        VARCHAR(32)    NOT NULL,
    policy_number       VARCHAR(32)    NOT NULL,
    claimant_name       VARCHAR(200)   NOT NULL,
    claimant_email      VARCHAR(320)   NOT NULL,
    claimant_phone      VARCHAR(32),
    status              VARCHAR(32)    NOT NULL,
    claimed_amount      NUMERIC(19, 2) NOT NULL,
    reserve_amount      NUMERIC(19, 2) NOT NULL DEFAULT 0,
    approved_amount     NUMERIC(19, 2),
    settled_amount      NUMERIC(19, 2),
    currency            VARCHAR(3)     NOT NULL,
    assigned_officer_id VARCHAR(64),
    decision_reason     VARCHAR(2000),
    payment_reference   VARCHAR(64),
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_claim_number UNIQUE (claim_number),
    CONSTRAINT ck_claim_status CHECK (status IN ('REPORTED', 'ASSIGNED', 'UNDER_REVIEW', 'INFO_REQUIRED',
                                                 'APPROVED', 'REJECTED', 'SETTLED')),
    CONSTRAINT ck_claim_claimed_positive CHECK (claimed_amount > 0),
    CONSTRAINT ck_claim_reserve_non_negative CHECK (reserve_amount >= 0),
    CONSTRAINT ck_claim_settled_le_approved CHECK (settled_amount IS NULL OR settled_amount <= approved_amount),
    CONSTRAINT ck_claim_assigned_before_review CHECK (status = 'REPORTED' OR assigned_officer_id IS NOT NULL)
);

CREATE INDEX ix_claim_status ON claim (status);
CREATE INDEX ix_claim_assigned_officer ON claim (assigned_officer_id) WHERE assigned_officer_id IS NOT NULL;
CREATE INDEX ix_claim_policy_number ON claim (policy_number);
CREATE INDEX ix_claim_created_at ON claim (created_at DESC);

CREATE TABLE incident
(
    id            UUID PRIMARY KEY,
    claim_id      UUID          NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    incident_type VARCHAR(32)   NOT NULL,
    incident_date DATE          NOT NULL,
    location      VARCHAR(500),
    description   VARCHAR(4000) NOT NULL,
    reported_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_incident_claim UNIQUE (claim_id)
);

CREATE TABLE assignment
(
    id          UUID PRIMARY KEY,
    claim_id    UUID        NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    officer_id  VARCHAR(64) NOT NULL,
    assigned_by VARCHAR(64) NOT NULL,
    note        VARCHAR(1000),
    assigned_at TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ,
    active      BOOLEAN     NOT NULL,
    CONSTRAINT ck_assignment_active CHECK (active = (released_at IS NULL))
);

-- At most one active assignment per claim.
CREATE UNIQUE INDEX ux_assignment_active_claim ON assignment (claim_id) WHERE active;
CREATE INDEX ix_assignment_claim ON assignment (claim_id);
CREATE INDEX ix_assignment_officer_active ON assignment (officer_id) WHERE active;

CREATE TABLE assessment
(
    id                  UUID PRIMARY KEY,
    claim_id            UUID          NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    assessor_id         VARCHAR(64)   NOT NULL,
    outcome             VARCHAR(32)   NOT NULL,
    findings            VARCHAR(4000) NOT NULL,
    recommended_reserve NUMERIC(19, 2),
    assessed_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_assessment_outcome CHECK (outcome IN ('RECOMMEND_APPROVAL', 'RECOMMEND_REJECTION', 'INFO_REQUIRED')),
    CONSTRAINT ck_assessment_reserve CHECK (recommended_reserve IS NULL OR recommended_reserve >= 0)
);

CREATE INDEX ix_assessment_claim ON assessment (claim_id, assessed_at);

-- Append-only audit trail of everything that happened to a claim.
CREATE TABLE claim_history
(
    id          UUID PRIMARY KEY,
    seq         BIGINT GENERATED ALWAYS AS IDENTITY,
    event_id    UUID        NOT NULL,
    claim_id    UUID        NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    event_type  VARCHAR(64) NOT NULL,
    from_status VARCHAR(32),
    to_status   VARCHAR(32),
    actor       VARCHAR(64),
    details     JSONB,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_claim_history_event UNIQUE (event_id)
);

CREATE INDEX ix_claim_history_claim ON claim_history (claim_id, seq);
