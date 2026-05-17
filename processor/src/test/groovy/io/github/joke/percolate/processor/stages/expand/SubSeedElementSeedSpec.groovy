package io.github.joke.percolate.processor.stages.expand

import io.github.joke.percolate.processor.graph.AccessPath
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.spi.Bridge
import io.github.joke.percolate.spi.BridgeStep
import io.github.joke.percolate.spi.ElementSeed
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.test.HarnessResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse
import java.util.List
import java.util.stream.Stream
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class SubSeedElementSeedSpec extends Specification {

    def 'SUB_SEED-triggered bridge query emits element seeds'() {
        given:
        final var ctx = HarnessResolveCtx.create()
        final var scope = new HarnessScope('mapList()')

        // Create a parent node for the ElementLocation nodes
        final var parentNode = new Node(
                Optional.of(TypeUniverse.STRING),
                new SourceLocation(AccessPath.of('in')),
                scope,
                Optional.empty())

        // Create source and target nodes for the SUB_SEED
        final var subFrom = new Node(
                Optional.of(TypeUniverse.STRING),
                new ElementLocation('from'),
                scope,
                Optional.of(parentNode))
        final var subTo = new Node(
                Optional.of(TypeUniverse.STRING),
                new ElementLocation('to'),
                scope,
                Optional.of(parentNode))

        // Create the SUB_SEED edge
        final var subSeedEdge = Edge.subSeed(subFrom, subTo, 'test.ListSetMap', Optional.empty())

        final var graph = new MapperGraph()
        graph.addNode(parentNode)
        graph.addNode(subFrom)
        graph.addNode(subTo)
        graph.addEdge(subSeedEdge)

        // Bridge that returns a step with element seeds
        final var elementSeed = new ElementSeed('element', TypeUniverse.STRING, TypeUniverse.STRING)
        final var bridge = new TestBridgeWithElementSeeds(TypeUniverse.STRING, TypeUniverse.STRING, elementSeed)

        when:
        final var phase = new BridgeSourceToTargetPhase(List.of(bridge), ctx)
        phase.apply(graph)

        then:
        // Verify ELEMENT_SEED edge was emitted
        final var elementSeedEdges = graph.edges().filter { it.kind == EdgeKind.ELEMENT_SEED }.toList()
        elementSeedEdges.size() == 1

        and:
        // Verify ElementLocation phantom nodes were created
        final var elementNodes = graph.nodes().filter { it.loc instanceof ElementLocation }.toList()
        elementNodes.size() >= 2

        and:
        // Verify the ELEMENT_SEED edge connects two ElementLocation nodes
        final var elementEdge = elementSeedEdges[0]
        elementEdge.from.loc instanceof ElementLocation
        elementEdge.to.loc instanceof ElementLocation

        and:
        // Verify the ELEMENT_SEED edge has a non-empty strategy FQN
        elementEdge.strategyClassFqn.isPresent()
        elementEdge.strategyClassFqn.get().contains('TestBridgeWithElementSeeds')
    }

    private static final class HarnessScope implements Scope {
        private final String name
        HarnessScope(final String name) { this.name = name }
        @Override String encode() { name }
    }

    private static final class TestBridgeWithElementSeeds implements Bridge {
        private final javax.lang.model.type.TypeMirror inputType
        private final javax.lang.model.type.TypeMirror outputType
        private final ElementSeed elementSeed

        TestBridgeWithElementSeeds(
                final javax.lang.model.type.TypeMirror inputType,
                final javax.lang.model.type.TypeMirror outputType,
                final ElementSeed elementSeed) {
            this.inputType = inputType
            this.outputType = outputType
            this.elementSeed = elementSeed
        }

        @Override
        public Stream<BridgeStep> bridge(
                final javax.lang.model.type.TypeMirror sourceType,
                final javax.lang.model.type.TypeMirror targetType,
                final ResolveCtx ctx) {
            if (ctx.types().isSameType(sourceType, inputType)
                    && ctx.types().isSameType(targetType, outputType)) {
                return Stream.of(new BridgeStep(
                        inputType, outputType, 1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, List.of(elementSeed)))
            }
            return Stream.empty()
        }
    }
}
