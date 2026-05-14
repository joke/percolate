package io.github.joke.percolate.test;

import io.github.joke.percolate.processor.graph.EdgeKind;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import org.assertj.core.api.AbstractAssert;

public final class ExpansionAssertions {

    private ExpansionAssertions() {}

    public static ExpansionAssert assertThat(final ExpansionResult result) {
        return new ExpansionAssert(result);
    }

    public static final class ExpansionAssert extends AbstractAssert<ExpansionAssert, ExpansionResult> {

        ExpansionAssert(final ExpansionResult result) {
            super(result, ExpansionAssert.class);
        }

        public void reachable(final String fromSource, final String toTarget) {
            if (actual == null || actual.expandedGraph() == null) {
                failWithMessage("Expected non-null result and graph");
            }
            final var found = hasRealisedPath(fromSource, toTarget);
            if (!found) {
                final var dot = renderDot();
                failWithMessage(
                        "Expected realised path from source '%s' to target '%s', but none found.\n%s",
                        fromSource, toTarget, dot);
            }
        }

        public Chain reportedError(final DiagnosticKind kind) {
            final var messages = actual.diagnostics();
            final var found = messages.stream().anyMatch(kind::matches);
            if (!found) {
                final var dot = renderDot();
                failWithMessage("Expected diagnostic kind %s, but got: %s\n%s", kind, messages, dot);
            }
            return new Chain(actual, kind);
        }

        private boolean hasRealisedPath(final String fromSource, final String toTarget) {
            final var graph = actual.expandedGraph();
            final var fromNodes = graph.nodes()
                    .filter(n -> n.getLoc() instanceof io.github.joke.percolate.processor.graph.SourceLocation
                            && ((io.github.joke.percolate.processor.graph.SourceLocation) n.getLoc())
                                    .getPath()
                                    .toString()
                                    .equals(fromSource))
                    .collect(java.util.stream.Collectors.toList());
            final var toNodes = graph.nodes()
                    .filter(n -> n.getLoc() instanceof io.github.joke.percolate.processor.graph.TargetLocation
                            && ((io.github.joke.percolate.processor.graph.TargetLocation) n.getLoc())
                                    .getPath()
                                    .toString()
                                    .equals(toTarget))
                    .collect(java.util.stream.Collectors.toList());

            if (fromNodes.isEmpty() || toNodes.isEmpty()) {
                return false;
            }

            for (final var from : fromNodes) {
                for (final var to : toNodes) {
                    if (bfsHasPath(from, to, graph)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean bfsHasPath(final Node from, final Node to, final MapperGraph graph) {
            final var visited = new java.util.HashSet<Node>();
            final var queue = new java.util.ArrayDeque<Node>();
            queue.add(from);
            visited.add(from);
            final var realisedEdges = graph.edges()
                    .filter(e -> e.getKind() == EdgeKind.REALISED)
                    .collect(java.util.stream.Collectors.toUnmodifiableList());
            while (!queue.isEmpty()) {
                final var current = queue.poll();
                if (current.equals(to)) {
                    return true;
                }
                enqueueRealisedSuccessors(current, realisedEdges, visited, queue);
            }
            return false;
        }

        private void enqueueRealisedSuccessors(
                final Node current,
                final java.util.List<io.github.joke.percolate.processor.graph.Edge> realisedEdges,
                final java.util.Set<Node> visited,
                final java.util.Deque<Node> queue) {
            for (final var edge : realisedEdges) {
                if (edge.getFrom().equals(current) && visited.add(edge.getTo())) {
                    queue.add(edge.getTo());
                }
            }
        }

        private String renderDot() {
            if (actual == null) {
                return "(no result)";
            }
            return actual.dotRender();
        }

        public enum DiagnosticKind {
            NO_PATH {
                @Override
                boolean matches(final String message) {
                    return message.contains("No realised path") || message.contains("no realised path");
                }
            },
            CYCLE {
                @Override
                boolean matches(final String message) {
                    return message.contains("Cycle detected") || message.contains("cycle");
                }
            },
            ROUND_CAP {
                @Override
                boolean matches(final String message) {
                    return message.contains("converge") || message.contains("round");
                }
            };

            abstract boolean matches(String message);
        }

        public static final class Chain {

            private final ExpansionResult result;
            private final DiagnosticKind kind;

            Chain(final ExpansionResult result, final DiagnosticKind kind) {
                this.result = result;
                this.kind = kind;
            }

            public void forSeedEdge(final String fromSource, final String toTarget) {
                final var messages = result.diagnostics();
                final var matching = messages.stream()
                        .filter(kind::matches)
                        .filter(m -> m.contains(fromSource) || m.contains(toTarget))
                        .collect(java.util.stream.Collectors.toList());
                if (matching.isEmpty()) {
                    final var dot = result.dotRender();
                    failWithMessage(
                            "Expected diagnostic kind %s for seed edge (%s -> %s), but got: %s\n%s",
                            kind, fromSource, toTarget, messages, dot);
                }
            }

            @SuppressWarnings("AnnotateFormatMethod")
            private void failWithMessage(final String format, final Object... args) {
                throw new AssertionError(String.format(format, args));
            }
        }
    }
}
