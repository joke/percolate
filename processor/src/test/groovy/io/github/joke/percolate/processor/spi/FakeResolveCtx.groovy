package io.github.joke.percolate.processor.spi

import javax.lang.model.util.Elements
import javax.lang.model.util.Types

/**
 * Test helper that wraps Compile Testing's Types/Elements into a ResolveCtx.
 */
class FakeResolveCtx implements ResolveCtx {

    private final Elements elements
    private final Types types

    FakeResolveCtx(final Elements elements, final Types types) {
        this.elements = elements
        this.types = types
    }

    @Override
    public Types types() {
        return types
    }

    @Override
    public Elements elements() {
        return elements
    }
}
