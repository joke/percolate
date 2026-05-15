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

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Provide
import net.jqwik.api.PropertyDefaults
import net.jqwik.api.Tag
import org.junit.jupiter.api.Timeout

import javax.lang.model.type.TypeMirror

@Tag('integration')
@Timeout(60)
@PropertyDefaults(tries = 100)
abstract class ExpansionPropertyBase {

    @Provide
    Arbitrary<MapperGraph> seedGraphs() {
        final Arbitrary<TypeMirror> type = Arbitraries.of(TypeUniverse.pool() as TypeMirror[])
        final Arbitrary<Integer> methodCount = Arbitraries.integers().between(1, 3)

        methodCount.flatMap { count ->
            Arbitraries.lazy {
                methodShapeArbitrary(type)
            }.list().ofSize(count).map { methods ->
                assembleGraph(methods)
            }
        }
    }

    @Provide
    Arbitrary<List<Bridge>> fakeBridges() {
        final Bridge[] alphabet = [
                new IdentityBridge(TypeUniverse.STRING, TypeUniverse.STRING),
                new IdentityBridge(TypeUniverse.INT,   TypeUniverse.INT),
                new ChainBridge(TypeUniverse.STRING, TypeUniverse.INT, TypeUniverse.LONG),
                new NoOpBridge()
        ] as Bridge[]
        Arbitraries.subsetOf(alphabet).map { it as List<Bridge> }
    }

    @Provide
    Arbitrary<List<SourceStep>> fakeSourceSteps() {
        Arbitraries.of([], [new NoOpSourceStep()])
    }

    @Provide
    Arbitrary<List<GroupTarget>> fakeGroupTargets() {
        Arbitraries.of([], [new NoOpGroupTarget()])
    }

    private static MapperGraph assembleGraph(final List<Map> methods) {
        final graph = new MapperGraph()
        methods.eachWithIndex { method, i ->
            final Scope scope = new HarnessScope("method${i}")
            final Map<String, Node> argNodes = [:]

            method.argTypes.eachWithIndex { argType, j ->
                final argName = "arg${j}"
                final node = new Node(
                        Optional.of(argType),
                        new SourceLocation(AccessPath.of(argName)),
                        scope,
                        Optional.empty()
                )
                argNodes[argName] = node
                graph.addNode(node)
            }

            final targetNode = new Node(
                    Optional.of(method.returnType),
                    new TargetLocation(TargetPath.of('out')),
                    scope,
                    Optional.empty()
            )
            graph.addNode(targetNode)

            argNodes.values().each { argNode ->
                graph.addEdge(Edge.elementSeed(argNode, targetNode, 'test.seed'))
            }
        }
        graph
    }

    private Arbitrary<Map> methodShapeArbitrary(final Arbitrary<TypeMirror> types) {
        final Arbitrary<Integer> argCount = Arbitraries.integers().between(1, 2)
        argCount.flatMap { n ->
            types.list().ofSize(n).flatMap { argTypes ->
                types.flatMap { returnType ->
                    Arbitraries.just([argTypes: argTypes, returnType: returnType])
                }
            }
        }
    }
}
