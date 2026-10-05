package com.insurer.claimflow.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI claimflowOpenApi(@Value("${claimflow.api.version:v1}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("ClaimFlow – Claims Management API")
                        .version(version)
                        .description("""
                                Submit, assign, assess, decide and settle insurance claims, and monitor workload and exposure.

                                Lifecycle: REPORTED → ASSIGNED → UNDER_REVIEW → APPROVED → SETTLED
                                (UNDER_REVIEW → REJECTED | INFO_REQUIRED; INFO_REQUIRED → UNDER_REVIEW).

                                Errors use RFC 7807 problem details. Invalid lifecycle transitions return 409,
                                business-rule violations 422, validation errors 400.""")
                        .contact(new Contact().name("Claims Platform Team"))
                        .license(new License().name("Proprietary")))
                .tags(List.of(
                        new Tag().name("Claim Intake").description("First notification of loss"),
                        new Tag().name("Claims").description("Claim queries"),
                        new Tag().name("Work Management").description("Claim assignment to officers"),
                        new Tag().name("Assessment").description("Claim review and reserve recommendations"),
                        new Tag().name("Lifecycle").description("Approval, rejection and settlement"),
                        new Tag().name("Exposure").description("Workload and financial exposure for managers"),
                        new Tag().name("Audit").description("Claim history"),
                        new Tag().name("Policy Reference").description("Policy lookup")));
    }
}
