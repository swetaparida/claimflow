package com.insurer.claimflow.lifecycle.domain;

import java.util.Objects;

public record Claimant(String name, String email, String phone) {

    public Claimant {
        Objects.requireNonNull(name, "claimant name is required");
        Objects.requireNonNull(email, "claimant email is required");
    }
}
