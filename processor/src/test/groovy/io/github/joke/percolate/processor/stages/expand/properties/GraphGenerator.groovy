package io.github.joke.percolate.processor.stages.expand.properties

import io.github.joke.percolate.processor.graph.AccessPath
import io.github.joke.percolate.processor.graph.Edge
import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.processor.graph.Node
import io.github.joke.percolate.processor.graph.Scope
import io.github.joke.percolate.processor.graph.SourceLocation
import io.github.joke.percolate.processor.graph.TargetLocation
import io.github.joke.percolate.processor.graph.TargetPath
import io.github.joke.percolate.processor.spi.Bridge
import io.github.joke.percolate.processor.spi.GroupTarget
import io.github.joke.percolate.processor.spi.SourceStep
import io.github.joke.percolate.processor.stages.expand.properties.fakes.ChainBridge
import io.github.joke.percolate.processor.stages.expand.properties.fakes.IdentityBridge
import io.github.joke.percolate.processor.stages.expand.properties.fakes.NoOpBridge
import io.github.joke.percolate.processor.stages.expand.properties.fakes.NoOpGroupTarget
import io.github.joke.percolate.processor.stages.expand.properties.fakes.NoOpSourceStep
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.processor.test.TypeUniverse

import javax.lang.model.type.TypeMirror

final class GraphGenerator {

    private static final Random RNG = new Random()
    private static final List<TypeMirror> TYPES = TypeUniverse.pool()

    private GraphGenerator() {}

    static MapperGraph randomSeed() {
        final graph = new MapperGraph()
        final methodCount = 1 + RNG.nextInt(3)
        (0..<methodCount).each { i -> buildMethod(graph, "method${i}") }
        graph
    }

    static List<Bridge> randomBridges() {
        final List<Bridge> bridges = []
        if (RNG.nextBoolean()) {
            bridges.add(new IdentityBridge(TypeUniverse.STRING, TypeUniverse.STRING))
        }
        if (RNG.nextBoolean()) {
            bridges.add(new IdentityBridge(TypeUniverse.INT, TypeUniverse.INT))
        }
        if (RNG.nextBoolean()) {
            bridges.add(new ChainBridge(TypeUniverse.STRING, TypeUniverse.INT, TypeUniverse.LONG))
        }
        if (RNG.nextBoolean()) {
            bridges.add(new NoOpBridge())
        }
        bridges
    }

    static List<SourceStep> randomSourceSteps() {
        RNG.nextBoolean() ? [new NoOpSourceStep()] : []
    }

    static List<GroupTarget> randomGroupTargets() {
        RNG.nextBoolean() ? [new NoOpGroupTarget()] : []
    }

    private static void buildMethod(final MapperGraph graph, final String scopeName) {
        final Scope scope = new HarnessScope(scopeName)
        final argCount = 1 + RNG.nextInt(2)
        final Map<String, Node> argNodes = [:]

        (0..<argCount).each { i ->
            final argName = "arg${i}"
            final type = TYPES[RNG.nextInt(TYPES.size())]
            final node = new Node(Optional.of(type), new SourceLocation(AccessPath.of(argName)), scope, Optional.empty())
            argNodes[argName] = node
            graph.addNode(node)
        }

        final returnType = TYPES[RNG.nextInt(TYPES.size())]
        final targetNode = new Node(
                Optional.of(returnType), new TargetLocation(TargetPath.of('out')), scope, Optional.empty())
        graph.addNode(targetNode)

        argNodes.values().each { argNode ->
            graph.addEdge(Edge.elementSeed(argNode, targetNode, 'test.seed'))
        }
    }
}
