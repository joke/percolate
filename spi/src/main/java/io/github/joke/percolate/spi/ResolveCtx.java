package io.github.joke.percolate.spi;

import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;

/**
 * The narrow, mockable type-query seam (change {@code type-query-seam}): the engine and strategies ask their type
 * questions here and treat every {@link TypeMirror} as an opaque pass-through token they never interrogate. The
 * seam methods are declared as defaults over {@link #types()}/{@link #elements()} for now; as consumers migrate,
 * the interrogation moves into the {@code CompileResolveCtx} implementation and {@code types()}/{@code elements()}
 * are removed (Phase 3), leaving these the sole type surface.
 */
public interface ResolveCtx {
    Types types();

    Elements elements();

    @Nullable
    CallableMethods callableMethods();

    /** Whether {@code a} and {@code b} denote the same type. */
    default boolean isSameType(final TypeMirror a, final TypeMirror b) {
        return types().isSameType(a, b);
    }

    /** Whether {@code type}'s erasure is {@code java.util.List}. */
    default boolean isList(final TypeMirror type) {
        return Containers.isList(type, this);
    }
}
