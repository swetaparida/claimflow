package com.insurer.claimflow.assessment.adapter.out.persistence;

import com.insurer.claimflow.assessment.application.port.out.AssessmentRepositoryPort;
import com.insurer.claimflow.assessment.domain.Assessment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
class AssessmentPersistenceAdapter implements AssessmentRepositoryPort {

    private final AssessmentJpaRepository repository;

    AssessmentPersistenceAdapter(AssessmentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(Assessment a) {
        repository.save(new AssessmentJpaEntity(a.id(), a.claimId(), a.assessorId(), a.outcome(), a.findings(),
                a.recommendedReserve(), a.assessedAt()));
    }

    @Override
    public List<Assessment> findByClaimId(UUID claimId) {
        return repository.findByClaimIdOrderByAssessedAtAsc(claimId).stream()
                .map(e -> new Assessment(e.getId(), e.getClaimId(), e.getAssessorId(), e.getOutcome(),
                        e.getFindings(), e.getRecommendedReserve(), e.getAssessedAt()))
                .toList();
    }
}
