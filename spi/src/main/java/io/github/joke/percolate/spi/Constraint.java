package io.github.joke.percolate.spi;

import java.util.List;
import java.util.Optional;

/**
 * A demand-scoped admissibility predicate a {@link DirectiveReader} contributes through
 * {@link DirectiveSink#constrain} (design D8 of change {@code decouple-engine-from-strategy-semantics}): it decides,
 * from a candidate {@link OperationSpec} and its bound ports alone, whether that candidate may land at the demand it
 * was attached to. The engine applies every constraint attached to a demand as a conjunction, treating each as
 * opaque — it interprets nothing about why a constraint refuses.
 *
 * <p>This generalises the engine's one existing enforcement primitive (the self-call rule); it is not a preference —
 * {@link OperationSpec#getWeight()} remains the only preference input to the cost fold, and admissibility can never
 * be expressed through it (see {@code demand-constraints}).
 */
@FunctionalInterface
public interface Constraint {

    /** Empty when {@code candidate} is admissible; otherwise the reason it is refused, positioned at a {@link Subject}. */
    Optional<Offer.Refusal> check(OperationSpec candidate, List<BoundPort> boundPorts);
}
