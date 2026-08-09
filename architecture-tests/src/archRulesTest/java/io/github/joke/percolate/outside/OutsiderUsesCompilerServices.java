package io.github.joke.percolate.outside;

import javax.lang.model.util.Types;

/** Violates: javax.lang.model.util is confined to the type-boundary packages. */
public class OutsiderUsesCompilerServices {
    public boolean sameType(final Types types) {
        return types != null;
    }
}
