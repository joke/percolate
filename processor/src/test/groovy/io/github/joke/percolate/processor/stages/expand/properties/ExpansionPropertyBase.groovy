package io.github.joke.percolate.processor.stages.expand.properties

import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.stages.expand.properties.fakes.*
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.Bridge
import io.github.joke.percolate.spi.GroupTarget
import io.github.joke.percolate.spi.test.TypeUniverse
import net.jqwik.api.*
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
                        scope
                )
                argNodes[argName] = node
                graph.addNode(node)
            }

            final targetNode = new Node(
                    Optional.of(method.returnType),
                    new TargetLocation(TargetPath.of('out')),
                    scope
            )
            graph.addNode(targetNode)

            argNodes.values().each { argNode ->
                graph.addEdge(Edge.seedForTest(argNode, targetNode))
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
