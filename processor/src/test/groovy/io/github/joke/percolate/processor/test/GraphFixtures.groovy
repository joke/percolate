package io.github.joke.percolate.processor.test

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.test.TypeUniverse

import javax.lang.model.type.TypeMirror

final class GraphFixtures {

    private static final EdgeCodegen NO_OP_CODEGEN = { vars, inputs -> CodeBlock.of('') }

    private GraphFixtures() {}

    static MapperGraph graphWithSeedAndRealisedPath() {
        final graph = new MapperGraph()
        final Scope scope = new HarnessScope('m(java.lang.String)')
        final source = node(scope, sourceLoc('in'), TypeUniverse.STRING)
        final target = node(scope, targetLoc('out'), TypeUniverse.STRING)
        graph.addNode(source)
        graph.addNode(target)
        graph.addEdge(source, target, Edge.seedForTest())
        graph.addEdge(source, target, Edge.realised(1, NO_OP_CODEGEN, 'Identity'))
        graph
    }

    static MapperGraph graphWithSubSeedCycle() {
        final graph = new MapperGraph()
        final Scope scope = new HarnessScope('cycle()')
        final seedSrc = node(scope, sourceLoc('in'), TypeUniverse.STRING)
        final seedTgt = node(scope, targetLoc('out'), TypeUniverse.STRING)
        final a = node(scope, sourceLoc('a'), TypeUniverse.INT)
        final b = node(scope, targetLoc('b'), TypeUniverse.INT)
        graph.addNode(seedSrc)
        graph.addNode(seedTgt)
        graph.addNode(a)
        graph.addNode(b)
        graph.addEdge(seedSrc, seedTgt, Edge.seedForTest())
        graph.addEdge(a, b, Edge.seed(Optional.empty()))
        graph.addEdge(b, a, Edge.seed(Optional.empty()))
        graph
    }

    static MapperGraph graphWithOrphanRealisedEdge() {
        final graph = new MapperGraph()
        final Scope scope = new HarnessScope('m()')
        final seedSrc = node(scope, sourceLoc('seedSrc'), TypeUniverse.STRING)
        final seedTgt = node(scope, targetLoc('seedTgt'), TypeUniverse.STRING)
        final stray1 = node(scope, sourceLoc('orphanA'), TypeUniverse.INT)
        final stray2 = node(scope, targetLoc('orphanB'), TypeUniverse.INT)
        graph.addNode(seedSrc)
        graph.addNode(seedTgt)
        graph.addNode(stray1)
        graph.addNode(stray2)
        graph.addEdge(seedSrc, seedTgt, Edge.seedForTest())
        graph.addEdge(stray1, stray2, Edge.realised(1, NO_OP_CODEGEN, 'Stray'))
        graph
    }

    private static Node node(final Scope scope, final SourceLocation loc, final TypeMirror type) {
        new Node(Optional.of(type), loc, scope)
    }

    private static Node node(final Scope scope, final TargetLocation loc, final TypeMirror type) {
        new Node(Optional.of(type), loc, scope)
    }

    private static SourceLocation sourceLoc(final String path) {
        new SourceLocation(AccessPath.of(path))
    }

    private static TargetLocation targetLoc(final String path) {
        new TargetLocation(TargetPath.of(path))
    }
}
