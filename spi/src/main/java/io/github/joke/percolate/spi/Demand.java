package io.github.joke.percolate.spi;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

/**
 * The common, myopic decision context handed to an {@link ExpansionStrategy}. It comes in two concrete shapes — a
 * {@link ProduceDemand} (handed to {@link ExpansionStrategy#expand}: "what produces this demanded target?") and a
 * {@link DescendDemand} (handed to {@link ExpansionStrategy#descend}: "what does reading this segment off this parent
 * yield?"). Both expose only what a strategy needs to make a <em>local</em> decision: this super carries the shared
 * nullness oracle, and deliberately exposes neither the graph nor any handle from which a strategy could traverse it,
 * nor any candidate snapshot of in-scope source Values — the engine, not the strategy, sources every input port
 * (design D1).
 */
public interface Demand {

    /** The nullness of {@code type} as declared at {@code scope} — the processor's nullability resolution. */
    Nullability nullnessOf(TypeMirror type, Element scope);
}
