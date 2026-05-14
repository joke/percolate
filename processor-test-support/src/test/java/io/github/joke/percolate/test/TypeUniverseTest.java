package io.github.joke.percolate.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class TypeUniverseTest {

    @Test
    void poolContainsExpectedNumberOfTypes() {
        assertThat(TypeUniverse.pool()).hasSize(10);
    }

    @Test
    void typesUtilIsConsistentWithFields() {
        assertThat(TypeUniverse.types().isSameType(TypeUniverse.STRING, TypeUniverse.STRING))
                .isTrue();
    }

    @Test
    void elementsUtilResolvesString() {
        assertThat(TypeUniverse.elements().getTypeElement("java.lang.String")).isNotNull();
    }

    @Test
    void primitiveAndBoxedAreDistinct() {
        assertThat(TypeUniverse.types().isSameType(TypeUniverse.INT, TypeUniverse.INTEGER))
                .isFalse();
    }
}
