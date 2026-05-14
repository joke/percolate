package io.github.joke.percolate.test;

import io.github.joke.percolate.processor.spi.CallableMethods;
import io.github.joke.percolate.processor.spi.MethodCandidate;
import io.github.joke.percolate.processor.spi.ResolveCtx;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import org.jspecify.annotations.Nullable;

public final class HarnessResolveCtx implements ResolveCtx {

    private static final HarnessResolveCtx INSTANCE = new HarnessResolveCtx();

    private HarnessResolveCtx() {}

    public static HarnessResolveCtx create() {
        return INSTANCE;
    }

    @Override
    public javax.lang.model.util.Types types() {
        return TypeUniverse.types();
    }

    @Override
    public Elements elements() {
        return TypeUniverse.elements();
    }

    @Override
    public @Nullable TypeElement mapperType() {
        return null;
    }

    @Override
    public @Nullable ExecutableElement currentMethod() {
        return null;
    }

    @Override
    public @Nullable CallableMethods callableMethods() {
        return new CallableMethods() {
            @Override
            public Stream<MethodCandidate> producing(final TypeMirror outputType) {
                return Stream.empty();
            }
        };
    }
}
