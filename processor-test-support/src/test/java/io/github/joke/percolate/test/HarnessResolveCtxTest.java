package io.github.joke.percolate.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class HarnessResolveCtxTest {

    @Test
    void typesSurfacedFromTypeUniverse() {
        assertThat(HarnessResolveCtx.create().types()).isSameAs(TypeUniverse.types());
    }

    @Test
    void elementsSurfacedFromTypeUniverse() {
        assertThat(HarnessResolveCtx.create().elements()).isSameAs(TypeUniverse.elements());
    }

    @Test
    void mapperTypeIsNull() {
        assertThat(HarnessResolveCtx.create().mapperType()).isNull();
    }

    @Test
    void currentMethodIsNull() {
        assertThat(HarnessResolveCtx.create().currentMethod()).isNull();
    }

    @Test
    void callableMethodsIsNotNull() {
        assertThat(HarnessResolveCtx.create().callableMethods()).isNotNull();
    }
}
