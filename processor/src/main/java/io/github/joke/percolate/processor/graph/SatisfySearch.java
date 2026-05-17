package io.github.joke.percolate.processor.graph;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

public final class SatisfySearch {
    private final MapperGraph graph;

    public SatisfySearch(final MapperGraph graph) {
        this.graph = graph;
    }

    public SatisfyResult satisfy(final Node target, final Set<Node> visited) {
        if (isSourceParameterNode(target)) {
            return SatisfyResult.sat();
        }
        if (visited.contains(target)) {
            return SatisfyResult.unsat("cycle detected at " + target.id(), null, 0);
        }
        final var visitedNew = new HashSet<Node>(visited);
        visitedNew.add(target);

        final var incomingEdges = graph.edges()
                .filter((Edge e) ->
                        e.getKind() == EdgeKind.REALISED && e.getTo().equals(target))
                .sorted((a, b) -> {
                    final var fqnA = a.getStrategyClassFqn().orElse("");
                    final var fqnB = b.getStrategyClassFqn().orElse("");
                    final var cmp = fqnA.compareTo(fqnB);
                    if (cmp != 0) return cmp;
                    return a.getFrom().id().compareTo(b.getFrom().id());
                })
                .collect(java.util.stream.Collectors.toList());

        if (incomingEdges.isEmpty()) {
            return SatisfyResult.unsat("no producer for " + target.id(), null, 0);
        }

        SatisfyResult deepestMiss = null;
        for (final var edge : incomingEdges) {
            final var result = satisfyEdge(edge, visitedNew);
            if (result.isSat()) {
                return SatisfyResult.sat();
            }
            if (deepestMiss == null
                    || result.depth() > deepestMiss.depth()
                    || (result.depth() == deepestMiss.depth()
                            && (deepestMiss.strategyFqn() == null
                                    || (result.strategyFqn() != null
                                            && result.strategyFqn().compareTo(deepestMiss.strategyFqn()) < 0)))) {
                deepestMiss = result;
            }
        }
        return deepestMiss != null
                ? deepestMiss
                : SatisfyResult.unsat("no incoming REALISED edges for " + target.id(), null, 0);
    }

    private SatisfyResult satisfyEdge(final Edge edge, final Set<Node> visited) {
        final var sourceResult = satisfy(edge.getFrom(), visited);
        if (sourceResult.isUnsat()) {
            return propagateOrFillSourceMiss(sourceResult, edge);
        }

        final var promises = collectPromises(edge);
        for (final var promise : promises) {
            final var promiseResult = satisfy(promise, visited);
            if (promiseResult.isUnsat()) {
                return propagateOrFillPromiseMiss(promiseResult, edge, promise);
            }
        }
        return SatisfyResult.sat();
    }

    private SatisfyResult propagateOrFillSourceMiss(final SatisfyResult deeper, final Edge edge) {
        if (deeper.strategyFqn() != null) {
            return SatisfyResult.unsat(
                    deeper.message(),
                    deeper.strategyFqn(),
                    deeper.depth() + 1,
                    deeper.edgeInputType(),
                    deeper.edgeOutputType(),
                    deeper.promiseKind(),
                    deeper.promiseInputType(),
                    deeper.promiseOutputType());
        }
        return SatisfyResult.unsat(
                "source not producible: " + edge.getFrom().id(),
                edge.getStrategyClassFqn().orElse(null),
                deeper.depth() + 1,
                typeString(edge.getFrom()),
                typeString(edge.getTo()),
                null,
                null,
                null);
    }

    private SatisfyResult propagateOrFillPromiseMiss(
            final SatisfyResult deeper, final Edge edge, final Node promise) {
        if (deeper.strategyFqn() != null) {
            return SatisfyResult.unsat(
                    deeper.message(),
                    deeper.strategyFqn(),
                    deeper.depth() + 1,
                    deeper.edgeInputType(),
                    deeper.edgeOutputType(),
                    deeper.promiseKind(),
                    deeper.promiseInputType(),
                    deeper.promiseOutputType());
        }
        return SatisfyResult.unsat(
                "promise unsatisfied: " + promise.id()
                        + " (from " + edge.getFrom().id()
                        + " via " + edge.getStrategyClassFqn().orElse("unknown") + ")",
                edge.getStrategyClassFqn().orElse(null),
                deeper.depth() + 1,
                typeString(edge.getFrom()),
                typeString(edge.getTo()),
                determinePromiseKind(edge, promise),
                resolvePromiseInputType(edge, promise),
                typeString(promise));
    }

    private String determinePromiseKind(final Edge edge, final Node promise) {
        final var elementSeed = graph.edges()
                .filter(e -> e.getKind() == EdgeKind.ELEMENT_SEED)
                .filter(e -> e.getFrom().getParent().isPresent()
                        && e.getFrom().getParent().get().equals(edge.getFrom()))
                .filter(e -> e.getTo().equals(promise))
                .findFirst();
        if (elementSeed.isPresent()) {
            return "element conversion";
        }
        return "chain step";
    }

    private @Nullable String resolvePromiseInputType(final Edge edge, final Node promise) {
        final var elementSeed = graph.edges()
                .filter(e -> e.getKind() == EdgeKind.ELEMENT_SEED)
                .filter(e -> e.getFrom().getParent().isPresent()
                        && e.getFrom().getParent().get().equals(edge.getFrom()))
                .filter(e -> e.getTo().equals(promise))
                .findFirst();
        if (elementSeed.isPresent()) {
            return typeString(elementSeed.get().getFrom());
        }
        return typeString(promise);
    }

    private String typeString(final Node node) {
        return node.getType().map(TypeMirror::toString).orElse("?");
    }

    private boolean isSourceParameterNode(final Node node) {
        if (!(node.getLoc() instanceof SourceLocation)) {
            return false;
        }
        final var sourceLoc = (SourceLocation) node.getLoc();
        return sourceLoc.getPath().getSegments().size() == 1 && node.getType().isPresent();
    }

    private List<Node> collectPromises(final Edge edge) {
        final var promises = new java.util.ArrayList<Node>();
        graph.edges()
                .filter(e -> e.getKind() == EdgeKind.ELEMENT_SEED)
                .filter(e -> e.getFrom().getParent().isPresent()
                        && e.getFrom().getParent().get().equals(edge.getFrom()))
                .map(e -> e.getTo())
                .forEach(promises::add);
        graph.edges()
                .filter(e -> e.getKind() == EdgeKind.SUB_SEED
                        && e.getFrom().equals(edge.getFrom())
                        && e.getStrategyClassFqn().equals(edge.getStrategyClassFqn()))
                .map(e -> e.getTo())
                .forEach(promises::add);
        return promises;
    }
}
