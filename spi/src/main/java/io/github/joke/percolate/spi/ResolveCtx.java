package io.github.joke.percolate.spi;

import io.github.joke.percolate.spi.types.TypeSpace;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;

public interface ResolveCtx {
    Types types();

    Elements elements();

    @Nullable
    CallableMethods callableMethods();

    /**
     * The owned type-currency snapshot for this mapper (change {@code evict-javax-model}, design D3/D9).
     * Transitional bridge: exposed <b>alongside</b> {@link #types()}/{@link #elements()} while consumers
     * migrate off {@code javax.lang.model} mirrors, and the sole type surface once those mirror accessors are
     * removed (the true "flip"). A pre-migration test harness inherits the throwing default until it is
     * migrated to build a {@link TypeSpace}.
     */
    default TypeSpace typeSpace() {
        throw new UnsupportedOperationException("no TypeSpace on this ResolveCtx (pre-migration harness)");
    }
}
