package io.github.joke.percolate.spi.builtins;

import io.github.joke.percolate.spi.ResolveCtx;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
        final List<Hop> queue = new ArrayList<>();
        queue.add(new Hop(start, 0));
        visited.add(start.toString());
        while (!queue.isEmpty()) {
            final var current = queue.remove(0);
            if (!ctx.isDeclared(current.type)) {
                continue;
            }
            final var directSupertype = ctx.superclassOf(current.type);
            if (!ctx.isDeclared(directSupertype)) {
                continue;
            }
            final var supKey = directSupertype.toString();
            if (visited.contains(supKey)) {
                continue;
            }
            visited.add(supKey);
            if (ctx.isSameType(directSupertype, target)) {
                return current.depth + 1;
            }
            queue.add(new Hop(directSupertype, current.depth + 1));
        }
        return 0;
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
