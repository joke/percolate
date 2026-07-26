package io.github.joke.percolate.spi;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * What an {@link ExpansionStrategy} answer carries for one demand (design D1 of change
 * {@code decouple-engine-from-strategy-semantics}): either a {@link Production} (an {@link OperationSpec} the
 * strategy can offer) or a {@link Refusal} ("this demand is mine, and I cannot serve it, because …"). An empty
 * {@code Stream<Offer>} keeps its existing meaning — "not mine" — and produces no diagnostic; a refusal is
 * reserved for a strategy that recognises the demand as its own but cannot serve it.
 *
 * <p>Construct only via {@link #of(OperationSpec)} and {@link #refusal(Subject, String)}.
 */
// Intentional pseudo-sealed base, mirroring PortType's Java 11 closed-hierarchy convention: a package-private
// constructor pins membership to the two leaves below, walked structurally by the engine rather than through a
// dispatch method.
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class Offer {

    /** Package-private to keep the shape pseudo-sealed: only the leaves below participate. */
    Offer() {}

    /** A production: the strategy can serve the demand with {@code spec}. */
    public static Offer of(final OperationSpec spec) {
        return new Production(spec);
    }

    /** A refusal: the strategy recognises the demand as its own but cannot serve it, and says why. */
    public static Offer refusal(final Subject subject, final String message) {
        return new Refusal(subject, message);
    }

    /** A production leaf: the {@link OperationSpec} the strategy offers. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static final class Production extends Offer {
        OperationSpec spec;
    }

    /** A refusal leaf: an opaque {@link Subject} position handle and the reason. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static final class Refusal extends Offer {
        Subject subject;
        String message;
    }
}
