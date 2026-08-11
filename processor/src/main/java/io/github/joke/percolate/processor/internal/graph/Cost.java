package io.github.joke.percolate.processor.internal.graph;

import java.util.Comparator;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import static java.lang.Boolean.compare;
import static java.util.Comparator.comparingInt;
import static lombok.AccessLevel.PRIVATE;

// The selection cost of a plan vertex (design D1): either .INFINITE (unreachable) or a finite,
// lexicographically-ordered vector (partials, weight) with partials — the transitive count of partial
// Operations (totality) — the most significant component, then weight. The minimum-cost-hyperpath fold combines
// costs with ⊕ =.min (OR at a Value) and ⊗ =.plus (AND at an Operation: INFINITE-absorbing componentwise add).
// A new selection preference is introduced as a new component here, leaving the fold untouched; reachability is
// derived as .isReachable().
@Getter
@ToString
@EqualsAndHashCode
@AllArgsConstructor(access = PRIVATE)
public final class Cost implements Comparable<Cost> {

    private static final Comparator<Cost> FINITE_ORDER =
            comparingInt(Cost::getPartials).thenComparingDouble(Cost::getWeight);

    // The unreachable cost: greater than every finite cost, and absorbing under .plus.
    public static final Cost INFINITE = new Cost(true, 0, 0.0);

    // The base-case cost of a supply root or a zero-port Operation.
    public static final Cost ZERO = new Cost(false, 0, 0.0);

    private final boolean infinite;
    private final int partials;
    private final double weight;

    // A finite cost: partials partial-operation count (totality), weight summed operation weight.
    public static Cost finite(final int partials, final double weight) {
        return new Cost(false, partials, weight);
    }

    public boolean isReachable() {
        return !infinite;
    }

    // ⊗ — componentwise add, INFINITE if either operand is (an Operation needs every port reachable).
    public Cost plus(final Cost other) {
        if (infinite || other.infinite) {
            return INFINITE;
        }
        return finite(partials + other.partials, weight + other.weight);
    }

    // ⊕ — the cheaper of two costs (lexicographic, INFINITE the greatest).
    public Cost min(final Cost other) {
        return compareTo(other) <= 0 ? this : other;
    }

    @Override
    public int compareTo(final Cost other) {
        if (infinite || other.infinite) {
            return compare(infinite, other.infinite);
        }
        return FINITE_ORDER.compare(this, other);
    }
}
