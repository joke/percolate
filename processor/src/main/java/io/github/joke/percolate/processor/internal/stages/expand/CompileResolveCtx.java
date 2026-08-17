package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.spi.CallableMethods;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

// The per-mapper ResolveCtx the expansion driver hands to strategies. It is constructed once per mapper inside
// ExpandStage.run from the injected Types/Elements and the mapper's discovered CallableMethods, so there is no
// ThreadLocal bridging a singleton context (design D6). It exposes only .types(), .elements(),
// .callableMethods(), and the generic .option(key) lookup over the raw -A processor options — the single
// option-reading seam every strategy uses (change add-builder-assembly), replacing the former per-feature
// .configuredTimeZone() accessor; mapperType/currentMethod were dead and are gone.
@RequiredArgsConstructor
final class CompileResolveCtx implements ResolveCtx {

    private final Elements elemElements;

    private final Types elemTypes;

    private final @Nullable CallableMethods elemCallableMethods;

    private final Map<String, String> elemOptions;

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
    public Optional<String> option(final String key) {
        return Optional.ofNullable(elemOptions.get(key));
    }
}
