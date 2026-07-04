package io.github.joke.percolate.spi.test

import io.github.joke.percolate.spi.CallableMethods
import io.github.joke.percolate.spi.MethodCandidate
import io.github.joke.percolate.spi.ResolveCtx

import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import java.util.stream.Stream

/**
 * A {@link ResolveCtx} backed by a private, non-shared javac substrate (change {@code evict-javax-model}, design
 * D8 amendment): each spec constructs its own instance over its own {@link PrivateTypeUniverse}, so nothing races
 * — see {@link PrivateTypeUniverse}'s own doc for why the sharing, not the realness, was the hazard.
 */
final class HarnessResolveCtx implements ResolveCtx {

    private static final CallableMethods EMPTY_CALLABLE_METHODS = new CallableMethods() {
        @Override
        Stream<MethodCandidate> producing(final TypeMirror outputType) {
            Stream.empty()
        }
    }

    private final PrivateTypeUniverse javac

    HarnessResolveCtx(final PrivateTypeUniverse javac) {
        this.javac = javac
    }

    @Override
    Types types() {
        javac.types()
    }

    @Override
    Elements elements() {
        javac.elements()
    }

    @Override
    CallableMethods callableMethods() {
        EMPTY_CALLABLE_METHODS
    }
}
