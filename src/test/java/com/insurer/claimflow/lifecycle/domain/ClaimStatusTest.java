package com.insurer.claimflow.lifecycle.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.insurer.claimflow.lifecycle.domain.ClaimStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class ClaimStatusTest {

    private static final Map<ClaimStatus, Set<ClaimStatus>> EXPECTED = Map.of(
            REPORTED, EnumSet.of(ASSIGNED),
            ASSIGNED, EnumSet.of(UNDER_REVIEW),
            UNDER_REVIEW, EnumSet.of(APPROVED, REJECTED, INFO_REQUIRED),
            INFO_REQUIRED, EnumSet.of(UNDER_REVIEW),
            APPROVED, EnumSet.of(SETTLED),
            REJECTED, EnumSet.noneOf(ClaimStatus.class),
            SETTLED, EnumSet.noneOf(ClaimStatus.class));

    static Stream<Arguments> allPairs() {
        return Stream.of(values()).flatMap(from -> Stream.of(values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void transitionMatrixMatchesSpecification(ClaimStatus from, ClaimStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(EXPECTED.get(from).contains(to));
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = {"REJECTED", "SETTLED"})
    void rejectedAndSettledAreTerminal(ClaimStatus status) {
        assertThat(status.isTerminal()).isTrue();
        assertThat(OPEN).doesNotContain(status);
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = {"REJECTED", "SETTLED"}, mode = EnumSource.Mode.EXCLUDE)
    void otherStatusesAreOpen(ClaimStatus status) {
        assertThat(status.isTerminal()).isFalse();
        assertThat(OPEN).contains(status);
    }
}
