package io.github.joke.percolate.spi.builtins.test

import io.github.joke.percolate.spi.CallableMethods
import io.github.joke.percolate.spi.MethodCandidate
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse

import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import java.util.stream.Stream

final class ResolveCtxBuilder {

    private Types types
    private Elements elements
    private CallableMethods callableMethods

    ResolveCtxBuilder() {
        this(TypeUniverse.types(), TypeUniverse.elements())
    }

    ResolveCtxBuilder(final Types types, final Elements elements) {
        this.types = types
        this.elements = elements
        this.callableMethods = new CallableMethods() {
            @Override
            Stream<MethodCandidate> producing(final TypeMirror outputType) {
                Stream.empty()
            }
        }
    }

    ResolveCtxBuilder withCallableMethods(final CallableMethods callableMethods) {
        final var copy = new ResolveCtxBuilder(this.types, this.elements)
        copy.callableMethods = callableMethods
        copy
    }

    ResolveCtx build() {
        new ResolveCtx() {
            @Override
            Types types() {
                ResolveCtxBuilder.this.types
            }

            @Override
            Elements elements() {
                ResolveCtxBuilder.this.elements
            }

            @Override
            CallableMethods callableMethods() {
                ResolveCtxBuilder.this.callableMethods
            }
        }
    }
}
