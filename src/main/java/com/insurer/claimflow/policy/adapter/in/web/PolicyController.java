package com.insurer.claimflow.policy.adapter.in.web;

import com.insurer.claimflow.policy.application.port.in.PolicyQueryUseCase;
import com.insurer.claimflow.policy.domain.Policy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies")
@Tag(name = "Policy Reference")
class PolicyController {

    private final PolicyQueryUseCase policies;

    PolicyController(PolicyQueryUseCase policies) {
        this.policies = policies;
    }

    @GetMapping("/{policyNumber}")
    @Operation(summary = "Look up a policy by number")
    Policy getPolicy(@PathVariable String policyNumber) {
        return policies.getPolicy(policyNumber);
    }
}
