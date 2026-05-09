package io.github.joke.percolate.processor.spi

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

/**
 * Test helper that wraps Compile Testing's Types/Elements into a ResolveCtx.
 */
class FakeResolveCtx implements ResolveCtx {

    private final Elements elements
    private final Types types
    private final TypeElement mapperType
    private final ExecutableElement currentMethod
    private final CallableMethods callableMethods

    FakeResolveCtx(final Elements elements, final Types types) {
        this(elements, types, null, null, null)
    }

    FakeResolveCtx(
            final Elements elements,
            final Types types,
            final TypeElement mapperType,
            final ExecutableElement currentMethod,
            final CallableMethods callableMethods) {
        this.elements = elements
        this.types = types
        this.mapperType = mapperType
        this.currentMethod = currentMethod
        this.callableMethods = callableMethods
    }

    @Override
    public Types types() {
        return types
    }

    @Override
    public Elements elements() {
        return elements
    }

    @Override
    public TypeElement mapperType() {
        return mapperType
    }

    @Override
    public ExecutableElement currentMethod() {
        return currentMethod
    }

    @Override
    public CallableMethods callableMethods() {
        return callableMethods
    }
}
