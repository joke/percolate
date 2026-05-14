package io.github.joke.percolate.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class StrategyBundleTest {

    @Test
    void emptyBundleHasNoBridges() {
        assertThat(StrategyBundle.empty().getBridges()).isEmpty();
    }

    @Test
    void emptyBundleHasNoSourceSteps() {
        assertThat(StrategyBundle.empty().getSourceSteps()).isEmpty();
    }

    @Test
    void emptyBundleHasNoGroupTargets() {
        assertThat(StrategyBundle.empty().getGroupTargets()).isEmpty();
    }
}
