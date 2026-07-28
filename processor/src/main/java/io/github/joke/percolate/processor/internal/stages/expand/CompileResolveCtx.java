package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.spi.CallableMethods;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Optional;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

// The per-mapper ResolveCtx the expansion driver hands to strategies. It is constructed once per mapper inside
// ExpandStage.run from the injected Types/Elements and the mapper's discovered CallableMethods, so there is no
// ThreadLocal bridging a singleton context (design D6). It exposes only .types(), .elements(),
// .callableMethods(), and .configuredTimeZone() — the project-wide -Apercolate.time.zone=… default the temporal
// zone bridge reads (design D4 of change add-temporal-type-mapping); mapperType/currentMethod were dead and are
// gone.
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
