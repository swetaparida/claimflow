package com.insurer.claimflow.assessment.domain;

/**
 * The assessor's recommendation. {@link #INFO_REQUIRED} parks the claim in INFO_REQUIRED until a further
 * assessment is recorded.
 */
public enum AssessmentOutcome {
    RECOMMEND_APPROVAL,
    RECOMMEND_REJECTION,
    INFO_REQUIRED
}
