package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.spi.CallableMethods;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Optional;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The per-mapper {@link ResolveCtx} the expansion driver hands to strategies. It is constructed once per mapper
 * inside {@link ExpandStage#run} from the injected {@link Types}/{@link Elements} and the mapper's discovered
 * {@link CallableMethods}, so there is no {@code ThreadLocal} bridging a singleton context (design D6). It exposes
 * only {@link #types()}, {@link #elements()}, {@link #callableMethods()}, and {@link #configuredTimeZone()} — the
 * project-wide {@code -Apercolate.time.zone=…} default the temporal zone bridge reads (design D4 of change
 * {@code add-temporal-type-mapping}); {@code mapperType}/{@code currentMethod} were dead and are gone.
 */
@RequiredArgsConstructor
final class CompileResolveCtx implements ResolveCtx {

    private final Elements elemElements;
    private final Types elemTypes;
    private final @Nullable CallableMethods elemCallableMethods;
    private final Optional<String> elemConfiguredTimeZone;

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

    @Override
    public Optional<String> configuredTimeZone() {
        return elemConfiguredTimeZone;
    }
}
