package io.github.joke.percolate.spi.builtins.test

import io.github.joke.percolate.spi.CallableMethods
import io.github.joke.percolate.spi.MethodCandidate
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import java.util.stream.Stream

final class ResolveCtxBuilder {

    private Types types
    private Elements elements
    private TypeElement mapperType
    private ExecutableElement currentMethod
    private CallableMethods callableMethods

    ResolveCtxBuilder() {
        this.types = TypeUniverse.types()
        this.elements = TypeUniverse.elements()
        this.mapperType = null
        this.currentMethod = null
        this.callableMethods = new CallableMethods() {
            @Override
            Stream<MethodCandidate> producing(final TypeMirror outputType) {
                Stream.empty()
            }
        }
    }

    ResolveCtxBuilder(final Types types, final Elements elements) {
        this.types = types
        this.elements = elements
        this.mapperType = null
        this.currentMethod = null
        this.callableMethods = new CallableMethods() {
            @Override
            Stream<MethodCandidate> producing(final TypeMirror outputType) {
                Stream.empty()
            }
        }
    }

    ResolveCtxBuilder withCallableMethods(final CallableMethods callableMethods) {
        final var copy = new ResolveCtxBuilder(this.types, this.elements)
        copy.mapperType = this.mapperType
        copy.currentMethod = this.currentMethod
        copy.callableMethods = callableMethods
        copy
    }

    ResolveCtxBuilder withMapperType(final TypeElement mapperType) {
        final var copy = new ResolveCtxBuilder(this.types, this.elements)
        copy.mapperType = mapperType
        copy.currentMethod = this.currentMethod
        copy.callableMethods = this.callableMethods
        copy
    }

    ResolveCtxBuilder withCurrentMethod(final ExecutableElement currentMethod) {
        final var copy = new ResolveCtxBuilder(this.types, this.elements)
        copy.mapperType = this.mapperType
        copy.currentMethod = currentMethod
        copy.callableMethods = this.callableMethods
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
            TypeElement mapperType() {
                ResolveCtxBuilder.this.mapperType
            }

            @Override
            ExecutableElement currentMethod() {
                ResolveCtxBuilder.this.currentMethod
            }

            @Override
            CallableMethods callableMethods() {
                ResolveCtxBuilder.this.callableMethods
            }
        }
    }
}
