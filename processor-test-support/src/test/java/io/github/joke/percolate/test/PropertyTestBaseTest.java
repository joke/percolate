package io.github.joke.percolate.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PropertyTestBaseTest {

    @Test
    void mapperSpecsArbitraryIsNotNull() {
        assertThat(new PropertyTestBase().mapperSpecs()).isNotNull();
    }

    @Test
    void strategyBundlesArbitraryIsNotNull() {
        assertThat(new PropertyTestBase().strategyBundles()).isNotNull();
    }
}
