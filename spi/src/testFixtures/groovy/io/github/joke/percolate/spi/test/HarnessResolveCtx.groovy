package io.github.joke.percolate.spi.test

import io.github.joke.percolate.spi.CallableMethods
import io.github.joke.percolate.spi.MethodCandidate
import io.github.joke.percolate.spi.ResolveCtx

import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import java.util.stream.Stream

final class HarnessResolveCtx implements ResolveCtx {

    private static final HarnessResolveCtx INSTANCE = new HarnessResolveCtx()
    private static final CallableMethods EMPTY_CALLABLE_METHODS = new CallableMethods() {
        @Override
        Stream<MethodCandidate> producing(final TypeMirror outputType) {
            Stream.empty()
        }
    }

    private HarnessResolveCtx() {}

    static HarnessResolveCtx create() {
        INSTANCE
    }

    @Override
    Types types() {
        TypeUniverse.types()
    }

    @Override
    Elements elements() {
        TypeUniverse.elements()
    }

    @Override
    CallableMethods callableMethods() {
        EMPTY_CALLABLE_METHODS
    }
}
