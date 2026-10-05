package com.insurer.claimflow.lifecycle.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ClaimJpaRepository extends JpaRepository<ClaimJpaEntity, UUID>, JpaSpecificationExecutor<ClaimJpaEntity> {

    @Override
    @EntityGraph(attributePaths = "incident")
    Page<ClaimJpaEntity> findAll(Specification<ClaimJpaEntity> spec, Pageable pageable);
}
