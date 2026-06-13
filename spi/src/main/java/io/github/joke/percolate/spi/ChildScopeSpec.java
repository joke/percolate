package io.github.joke.percolate.spi;

import javax.lang.model.type.TypeMirror;
import lombok.Value;

/**
 * The child-scope declaration of a scope-owning {@link OperationSpec} (a container element mapping): the element
 * types and nullness of the child plan's param-root (element in) and return-root (element out). The driver mints
 * these two roots inside the freshly created child scope when the operation lands; no dependency edge crosses the
 * scope boundary — the owning operation is the only coupling.
 */
@Value
public class ChildScopeSpec {
    TypeMirror elementIn;
    Nullability elementInNullness;
    TypeMirror elementOut;
    Nullability elementOutNullness;
}
