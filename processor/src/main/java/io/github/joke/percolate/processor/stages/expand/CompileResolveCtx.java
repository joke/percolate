package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.spi.CallableMethods;
import io.github.joke.percolate.spi.ResolveCtx;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The per-mapper {@link ResolveCtx} the expansion driver hands to strategies. It is constructed once per mapper
 * inside {@link ExpandStage#run} from the injected {@link Types}/{@link Elements} and the mapper's discovered
 * {@link CallableMethods}, so there is no {@code ThreadLocal} bridging a singleton context (design D6). It exposes
 * only {@link #types()}, {@link #elements()}, and {@link #callableMethods()} — {@code mapperType}/{@code currentMethod}
 * were dead and are gone.
 */
@RequiredArgsConstructor
final class CompileResolveCtx implements ResolveCtx {

    private final Elements elemElements;
    private final Types elemTypes;
    private final @Nullable CallableMethods elemCallableMethods;

    @Override
    public Types types() {
        return elemTypes;
    }

    @Override
    public Elements elements() {
        return elemElements;
    }

    @Override
    public @Nullable CallableMethods callableMethods() {
        return elemCallableMethods;
    }
}
