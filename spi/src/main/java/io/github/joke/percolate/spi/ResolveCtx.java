package io.github.joke.percolate.spi;

import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;

public interface ResolveCtx {
    Types types();

    Elements elements();

    @Nullable
    CallableMethods callableMethods();
}
