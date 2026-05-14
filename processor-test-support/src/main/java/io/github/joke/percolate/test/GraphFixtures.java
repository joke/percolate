package io.github.joke.percolate.test;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.graph.AccessPath;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.EdgeCodegen;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.graph.TargetPath;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

public final class GraphFixtures {

    private static final EdgeCodegen NO_OP_CODEGEN = (vars, inputs) -> CodeBlock.of("");

    private GraphFixtures() {}

    public static MapperGraph graphWithSeedAndRealisedPath() {
        final var graph = new MapperGraph();
        final Scope scope = new HarnessScope("m(java.lang.String)");
        final var source = node(scope, sourceLoc("in"), TypeUniverse.STRING);
        final var target = node(scope, targetLoc("out"), TypeUniverse.STRING);
        graph.addNode(source);
        graph.addNode(target);
        graph.addEdge(Edge.elementSeed(source, target, "test.seed"));
        graph.addEdge(Edge.realised(source, target, 1, Optional.empty(), NO_OP_CODEGEN, "Identity"));
        return graph;
    }

    public static MapperGraph graphWithSubSeedCycle() {
        final var graph = new MapperGraph();
        final Scope scope = new HarnessScope("cycle()");
        final var seedSrc = node(scope, sourceLoc("in"), TypeUniverse.STRING);
        final var seedTgt = node(scope, targetLoc("out"), TypeUniverse.STRING);
        final var a = node(scope, sourceLoc("a"), TypeUniverse.INT);
        final var b = node(scope, targetLoc("b"), TypeUniverse.INT);
        graph.addNode(seedSrc);
        graph.addNode(seedTgt);
        graph.addNode(a);
        graph.addNode(b);
        graph.addEdge(Edge.elementSeed(seedSrc, seedTgt, "test.seed"));
        graph.addEdge(Edge.subSeed(a, b, "cycle.strategy", Optional.empty()));
        graph.addEdge(Edge.subSeed(b, a, "cycle.strategy", Optional.empty()));
        return graph;
    }

    public static MapperGraph graphWithOrphanRealisedEdge() {
        final var graph = new MapperGraph();
        final Scope scope = new HarnessScope("m()");
        final var seedSrc = node(scope, sourceLoc("seedSrc"), TypeUniverse.STRING);
        final var seedTgt = node(scope, targetLoc("seedTgt"), TypeUniverse.STRING);
        final var stray1 = node(scope, sourceLoc("orphanA"), TypeUniverse.INT);
        final var stray2 = node(scope, targetLoc("orphanB"), TypeUniverse.INT);
        graph.addNode(seedSrc);
        graph.addNode(seedTgt);
        graph.addNode(stray1);
        graph.addNode(stray2);
        graph.addEdge(Edge.elementSeed(seedSrc, seedTgt, "test.seed"));
        graph.addEdge(Edge.realised(stray1, stray2, 1, Optional.empty(), NO_OP_CODEGEN, "Stray"));
        return graph;
    }

    private static Node node(final Scope scope, final SourceLocation loc, final TypeMirror type) {
        return new Node(Optional.of(type), loc, scope, Optional.empty());
    }

    private static Node node(final Scope scope, final TargetLocation loc, final TypeMirror type) {
        return new Node(Optional.of(type), loc, scope, Optional.empty());
    }

    private static SourceLocation sourceLoc(final String path) {
        return new SourceLocation(AccessPath.of(path));
    }

    private static TargetLocation targetLoc(final String path) {
        return new TargetLocation(TargetPath.of(path));
    }
}
