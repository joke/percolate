package io.github.joke.percolate.processor.test

import io.github.joke.percolate.processor.graph.*

final class ExpansionAssertions {

    private ExpansionAssertions() {}

    static ExpansionAssert assertThat(final ExpansionResult result) {
        new ExpansionAssert(result)
    }

    static final class ExpansionAssert {

        private final ExpansionResult actual

        ExpansionAssert(final ExpansionResult actual) {
            this.actual = actual
        }

        void reachable(final String fromSource, final String toTarget) {
            if (actual == null || actual.expandedGraph() == null) {
                fail('Expected non-null result and graph')
            }
            if (!hasRealisedPath(fromSource, toTarget)) {
                fail("Expected realised path from source '${fromSource}' to target '${toTarget}', but none found.\n${renderDot()}")
            }
        }

        Chain reportedError(final DiagnosticKind kind) {
            final messages = actual.diagnostics()
            if (!messages.any { kind.matches(it) }) {
                fail("Expected diagnostic kind ${kind}, but got: ${messages}\n${renderDot()}")
            }
            new Chain(actual, kind)
        }

        private static void fail(final String message) {
            throw new AssertionError((Object) message)
        }

        private boolean hasRealisedPath(final String fromSource, final String toTarget) {
            final graph = actual.expandedGraph()
            final fromNodes = graph.nodes()
                    .filter { it.loc instanceof SourceLocation && ((SourceLocation) it.loc).path.toString() == fromSource }
                    .toList()
            final toNodes = graph.nodes()
                    .filter { it.loc instanceof TargetLocation && ((TargetLocation) it.loc).path.toString() == toTarget }
                    .toList()

            if (fromNodes.empty || toNodes.empty) {
                return false
            }
            for (final from in fromNodes) {
                for (final to in toNodes) {
                    if (bfsHasPath(from, to, graph)) {
                        return true
                    }
                }
            }
            false
        }

        private boolean bfsHasPath(final Node from, final Node to, final MapperGraph graph) {
            final Set<Node> visited = [] as Set
            final queue = new ArrayDeque<Node>()
            queue.add(from)
            visited.add(from)
            final realisedEdges = graph.edges().filter { it.kind == EdgeKind.REALISED }.toList()
            while (!queue.empty) {
                final current = queue.poll()
                if (current == to) {
                    return true
                }
                enqueueRealisedSuccessors(current, realisedEdges, visited, queue, graph)
            }
            false
        }

        private void enqueueRealisedSuccessors(
                final Node current, final List<Edge> realisedEdges, final Set<Node> visited, final Deque<Node> queue,
                final MapperGraph graph) {
            for (final edge in realisedEdges) {
                if (graph.getEdgeSource(edge) == current && visited.add(graph.getEdgeTarget(edge))) {
                    queue.add(graph.getEdgeTarget(edge))
                }
            }
        }

        private String renderDot() {
            actual == null ? '(no result)' : actual.dotRender()
        }

        static enum DiagnosticKind {
            NO_PATH {
                @Override
                boolean matches(final String message) {
                    message.contains('No realised path') || message.contains('no realised path')
                }
            },
            CYCLE {
                @Override
                boolean matches(final String message) {
                    message.contains('Cycle detected') || message.contains('cycle')
                }
            },
            ROUND_CAP {
                @Override
                boolean matches(final String message) {
                    message.contains('converge') || message.contains('round')
                }
            }

            abstract boolean matches(String message)
        }

        static final class Chain {

            private final ExpansionResult result
            private final DiagnosticKind kind

            Chain(final ExpansionResult result, final DiagnosticKind kind) {
                this.result = result
                this.kind = kind
            }

            void forSeedEdge(final String fromSource, final String toTarget) {
                final messages = result.diagnostics()
                final matching = messages.findAll { kind.matches(it) && (it.contains(fromSource) || it.contains(toTarget)) }
                if (matching.empty) {
                    throw new AssertionError((Object)
                            "Expected diagnostic kind ${kind} for seed edge (${fromSource} -> ${toTarget}), but got: ${messages}\n${result.dotRender()}")
                }
            }
        }
    }
}
