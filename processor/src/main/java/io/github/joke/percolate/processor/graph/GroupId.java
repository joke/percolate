package io.github.joke.percolate.processor.graph;

import java.util.concurrent.atomic.AtomicInteger;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A thin value-type identity for an {@link ExpansionGroup}: a monotonic {@code int} with cheap equality and
 * ordering, plus a {@code seed} marker recording whether the group was registered by the seed stage (a structural
 * scaffolding demand) or minted during expansion (a producer sub-group). Identity is the {@code value} alone — the
 * {@code seed} flag is metadata used for plan-selection and dispatch, not for equality.
 */
@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class GroupId implements Comparable<GroupId> {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @EqualsAndHashCode.Include
    private final int value;

    private final boolean seed;

    /** Mints the next monotonic id; {@code seed} marks a seed-stage scaffolding demand. */
    public static GroupId next(final boolean seed) {
        return new GroupId(COUNTER.getAndIncrement(), seed);
    }

    @Override
    public int compareTo(final GroupId other) {
        return Integer.compare(value, other.value);
    }

    @Override
    public String toString() {
        return (seed ? "g!" : "g") + value;
    }
}
