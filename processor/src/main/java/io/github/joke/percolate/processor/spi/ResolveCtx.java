package io.github.joke.percolate.processor.spi;

import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public interface ResolveCtx {
    Types types();

    Elements elements();
}
