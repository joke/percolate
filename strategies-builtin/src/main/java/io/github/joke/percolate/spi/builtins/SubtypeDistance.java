package io.github.joke.percolate.spi.builtins;

import io.github.joke.percolate.spi.ResolveCtx;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

/**
 * The subtype-distance walk {@link MethodCallBridge} uses to weight a candidate return type: a breadth-first count of
 * superclass hops from {@code from} up to {@code to}. Extracted so the walk is testable in isolation over the mocked
 * {@link ResolveCtx} seam (change {@code cutover-strategies-to-mock-seam}, design D2) rather than only through a real
 * type hierarchy. Kept as a hand-rolled BFS rather than a library graph primitive: the supertype chain is derived
 * on-demand from {@link ResolveCtx#superclassOf}, so building a graph structure first would be more scaffolding than
 * the walk it replaces.
 */
final class SubtypeDistance {

    int between(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        if (ctx.isSameType(from, to)) {
            return 0;
        }
        if (!ctx.isAssignable(from, to)) {
            // FOLLOW-UP: same-type and non-assignable both collapse to distance 0 here — a distance and a "no path"
            // outcome are conflated. Carried forward unchanged from the pre-extraction MethodCallBridge behaviour;
            // pinned mock-only in SubtypeDistanceSpec.
            return 0;
        }
        return bfsDistance(from, to, ctx);
    }

    int bfsDistance(final TypeMirror start, final TypeMirror target, final ResolveCtx ctx) {
        if (ctx.isSameType(start, target)) {
            return 0;
        }
        final Set<String> visited = new HashSet<>();
        visited.add(start.toString());
        final Deque<Hop> queue = new ArrayDeque<>();
        queue.add(new Hop(start, 0));
        while (!queue.isEmpty()) {
            final var distance = advance(queue.remove(), target, visited, queue, ctx);
            if (distance.isPresent()) {
                return distance.get();
            }
        }
        return 0;
    }

    /**
     * One BFS step from {@code current}: the distance to {@code target} when its unvisited direct supertype is a
     * match, else empty — enqueueing that supertype as the next hop when it is not.
     */
    Optional<Integer> advance(
            final Hop current,
            final TypeMirror target,
            final Set<String> visited,
            final Deque<Hop> queue,
            final ResolveCtx ctx) {
        final var hop = nextHop(current, visited, ctx);
        if (hop.isEmpty()) {
            return Optional.empty();
        }
        if (ctx.isSameType(hop.get().type, target)) {
            return Optional.of(hop.get().depth);
        }
        queue.add(hop.get());
        return Optional.empty();
    }

    /** The unvisited direct supertype hop of {@code current}, or empty when there is none left to walk. */
    Optional<Hop> nextHop(final Hop current, final Set<String> visited, final ResolveCtx ctx) {
        if (!ctx.isDeclared(current.type)) {
            return Optional.empty();
        }
        final var directSupertype = ctx.superclassOf(current.type);
        if (!ctx.isDeclared(directSupertype) || !visited.add(directSupertype.toString())) {
            return Optional.empty();
        }
        return Optional.of(new Hop(directSupertype, current.depth + 1));
    }

    private static final class Hop {
        private final TypeMirror type;
        private final int depth;

        Hop(final TypeMirror type, final int depth) {
            this.type = type;
            this.depth = depth;
        }
    }
}
