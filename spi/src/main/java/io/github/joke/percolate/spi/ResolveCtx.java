package io.github.joke.percolate.spi;

import org.jspecify.annotations.Nullable;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public interface ResolveCtx {
    Types types();

    Elements elements();

    @Nullable
    TypeElement mapperType();

    @Nullable
    ExecutableElement currentMethod();

    @Nullable
    CallableMethods callableMethods();
}
