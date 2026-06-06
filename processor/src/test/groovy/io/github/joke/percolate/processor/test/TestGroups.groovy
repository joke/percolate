package io.github.joke.percolate.processor.test

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.Edge
import io.github.joke.percolate.processor.graph.EdgeKind
import io.github.joke.percolate.processor.graph.ExpansionGroup
import io.github.joke.percolate.processor.graph.GroupId
import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.processor.graph.Node
import io.github.joke.percolate.spi.EdgeCodegen

/**
 * Test adapter for the dissolved {@link ExpansionGroup}: reproduces the old
 * {@code ExpansionGroup.of(root, slots, codegen, fqn, edges, graph)} factory in the tag-based model. It mints a
 * {@link GroupId} (seed-ness inferred from the strategy fqn), tags {@code root} + {@code slots} (and any provided
 * view edges' endpoints), and ensures the scaffolding edge each {@code slot} needs so the group's derived
 * {@code inputs()} recovers the demand slots — a {@code SEED} edge for a seed group, a {@code REALISED} edge for a
 * sub-group (mirroring what {@code SeedStage} / {@code FrontierMatcher} emit in production).
 */
class TestGroups {

    private static final String SEED_PKG = 'io.github.joke.percolate.processor.stages.seed.'
    private static final EdgeCodegen NOOP = { vars, inputs -> CodeBlock.of('') }

    static ExpansionGroup of(Node root, List<Node> slots, String fqn, Set<Edge> edges, MapperGraph graph) {
        def seed = fqn.startsWith(SEED_PKG)
        slots.each { slot -> ensureSlotEdge(graph, slot, root, seed, fqn, edges) }
        def id = GroupId.next(seed)
        def group = new ExpansionGroup(id, root, graph)
        root.joinGroup(id)
        slots.each { it.joinGroup(id) }
        edges.each { it.from.joinGroup(id); it.to.joinGroup(id) }
        graph.addGroup(group)
        group
    }

    static ExpansionGroup of(Node root, List<Node> slots, String fqn, MapperGraph graph) {
        of(root, slots, fqn, [] as Set, graph)
    }

    private static void ensureSlotEdge(MapperGraph graph, Node slot, Node root, boolean seed, String fqn, Set<Edge> edges) {
        def kind = seed ? EdgeKind.SEED : EdgeKind.REALISED
        def present = graph.edges().anyMatch { e -> e.from.is(slot) && e.to.is(root) && e.kind == kind } ||
                edges.any { e -> e.from.is(slot) && e.to.is(root) }
        if (present) {
            return
        }
        def edge = seed
                ? Edge.seed(slot, root, Optional.empty(), Optional.empty())
                : Edge.realised(slot, root, 1, NOOP, fqn)
        graph.addEdge(edge)
    }
}
