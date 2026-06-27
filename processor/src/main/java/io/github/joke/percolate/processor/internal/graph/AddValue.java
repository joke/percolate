package io.github.joke.percolate.processor.internal.graph;

import io.github.joke.percolate.spi.Nullability;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

/**
 * Names a {@link io.github.joke.percolate.processor.internal.graph.Value} by its full identity key
 * {@code (scope, location, type, nullness)}. Application is the get-or-create dedup of
 * {@code MapperGraph.valueFor}: an existing Value of the same key is reused, a missing one is minted. The same
 * carrier names the feeding Values inside an {@link AddOperation}'s port bindings and its output.
 */
@Value
public class AddValue implements GraphDelta {
    Scope scope;
    Location location;
    TypeMirror type;
    Nullability nullness;
}
