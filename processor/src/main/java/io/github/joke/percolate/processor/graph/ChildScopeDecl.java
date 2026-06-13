package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.spi.Nullability;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

/**
 * The child-scope declaration of a scope-owning {@link AddOperation} (container element mapping): the element
 * types and nullness of the child plan's param-root (element in) and return-root (element out) Values, minted
 * inside the freshly created {@link ChildScope} when the Operation lands.
 */
@Value
public class ChildScopeDecl {
    TypeMirror elementIn;
    Nullability elementInNullness;
    TypeMirror elementOut;
    Nullability elementOutNullness;
}
