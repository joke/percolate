package io.github.joke.percolate.spi.test

import io.github.joke.percolate.spi.CallableMethods
import io.github.joke.percolate.spi.MethodCandidate
import io.github.joke.percolate.spi.ResolveCtx

import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import java.util.stream.Stream

/**
 * A {@link ResolveCtx} over a chosen javac substrate. Constructed either over a private, non-shared
 * {@link PrivateTypeUniverse} (one per spec, so nothing races — see {@link PrivateTypeUniverse}'s doc for why the
 * <em>sharing</em>, not the realness, was the hazard) or, via {@link #create()}, over the shared static
 * {@link TypeUniverse} so a spec whose type literals come from {@code TypeUniverse} shares the same substrate.
 * Kept transitionally under the {@code type-query-seam} change until the {@code processor}/{@code spi} specs that
 * use it move to a mocked {@code ResolveCtx}.
 */
final class HarnessResolveCtx implements ResolveCtx {

    private static final CallableMethods EMPTY_CALLABLE_METHODS = new CallableMethods() {
        @Override
        Stream<MethodCandidate> producing(final TypeMirror outputType) {
            Stream.empty()
        }
    }

    private final Types types
    private final Elements elements

    HarnessResolveCtx(final PrivateTypeUniverse javac) {
        this(javac.types(), javac.elements())
    }

    HarnessResolveCtx(final Types types, final Elements elements) {
        this.types = types
        this.elements = elements
    }

    static HarnessResolveCtx create() {
        new HarnessResolveCtx(TypeUniverse.types(), TypeUniverse.elements())
    }

    @Override
    Types types() {
        types
    }

    @Override
    Elements elements() {
        elements
    }

    @Override
    CallableMethods callableMethods() {
        EMPTY_CALLABLE_METHODS
    }
}
