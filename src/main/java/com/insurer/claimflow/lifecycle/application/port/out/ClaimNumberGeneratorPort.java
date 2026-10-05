package com.insurer.claimflow.lifecycle.application.port.out;

public interface ClaimNumberGeneratorPort {

    /** Returns a new, unique, human-readable claim number, e.g. {@code CLM-2026-00000042}. */
    String nextClaimNumber();
}
