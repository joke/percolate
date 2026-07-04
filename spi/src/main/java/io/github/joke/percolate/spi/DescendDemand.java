package io.github.joke.percolate.spi;

import io.github.joke.percolate.spi.types.TypeRef;
import io.github.joke.percolate.spi.types.TypeRefs;
import javax.lang.model.type.TypeMirror;

/**
 * The descend shape of a {@link Demand}, handed to {@link ExpansionStrategy#descend}: an accessor answers "what does
 * reading this segment off this parent yield?". It carries the concrete {@link #parentType()} (and
 * {@link #parentNullness()}) being descended and the single source-path {@link #segment()} to resolve. Unlike a
 * {@link ProduceDemand}, the produced output type is the strategy's <em>answer</em>, discovered from the member it
 * resolves — there is no target type to key on, and the parent is <b>not</b> punned as a {@code targetType}.
 */
public interface DescendDemand extends Demand {

    /** The concrete parent type the accessor reads its segment off. */
    TypeMirror parentType();

    /**
     * The owned-model counterpart of {@link #parentType} (change {@code evict-javax-model}, design D9 transitional
     * bridge): {@link TypeRefs#of}({@link #parentType()}), derived automatically — no implementer overrides this.
     * Not safe for a site requiring exact JLS fidelity (design D7 amendment: v1's {@link TypeRef} has no wildcard
     * representation); fine for structural matching against {@code TypeSpace}.
     */
    default TypeRef parentTypeRef() {
        return TypeRefs.of(parentType());
    }

    /** The nullness of the parent value being descended. */
    Nullability parentNullness();

    /** The single source-path segment to resolve on the parent (e.g. {@code street} for {@code address.street}). */
    String segment();
}
