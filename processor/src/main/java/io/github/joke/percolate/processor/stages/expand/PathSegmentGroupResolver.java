package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.ExpansionGroup;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.spi.PathSegmentResolver;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.ResolvedSegment;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
final class PathSegmentGroupResolver {

    private static final int SINGLE_SLOT = 1;

    private final List<PathSegmentResolver> resolvers;

    static boolean isPathSegmentGroup(final ExpansionGroup group) {
        if (group.getSlots().size() != SINGLE_SLOT) {
            return false;
        }
        final var root = group.getRoot();
        final var slot = group.getSlots().get(0);
        if (!(root.getLoc() instanceof SourceLocation) || !(slot.getLoc() instanceof SourceLocation)) {
            return false;
        }
        final var rootSegs = ((SourceLocation) root.getLoc()).getPath().getSegments();
        final var slotSegs = ((SourceLocation) slot.getLoc()).getPath().getSegments();
        return rootSegs.size() == slotSegs.size() + 1
                && rootSegs.subList(0, slotSegs.size()).equals(slotSegs);
    }

    static String appendedSegment(final ExpansionGroup group) {
        final var rootSegs =
                ((SourceLocation) group.getRoot().getLoc()).getPath().getSegments();
        return rootSegs.get(rootSegs.size() - 1);
    }

    Optional<Match> resolveFor(final ExpansionGroup group, final ResolveCtx ctx) {
        final Node slot = group.getSlots().get(0);
        if (slot.getType().isEmpty()) {
            return Optional.empty();
        }
        final var segment = appendedSegment(group);
        @Nullable Match best = null;
        for (final var resolver : resolvers) {
            final var resolved = resolver.resolve(slot.getType().get(), segment, ctx);
            if (resolved.isEmpty()) {
                continue;
            }
            if (best == null || resolved.get().getWeight() < best.segment.getWeight()) {
                best = new Match(resolved.get(), resolver.getClass().getName());
            }
        }
        return Optional.ofNullable(best);
    }

    static final class Match {
        final ResolvedSegment segment;
        final String resolverClassName;

        Match(final ResolvedSegment segment, final String resolverClassName) {
            this.segment = segment;
            this.resolverClassName = resolverClassName;
        }
    }
}
