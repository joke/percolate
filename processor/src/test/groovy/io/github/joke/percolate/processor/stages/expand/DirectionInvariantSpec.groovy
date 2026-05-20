package io.github.joke.percolate.processor.stages.expand

import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.test.ExpansionHarness
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.*
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * Direction-invariant test: asserts that Bridges are always queried with the frontier
 * type as outputType, never as inputType. This is the guard rail for the
 * feedback_never_forward_expansion invariant.
 */
@Tag('unit')
@Tag('direction-invariant')
@Timeout(30)
class DirectionInvariantSpec extends Specification {

    def 'Bridge mock is called with outputType == frontier type, not inputType'() {
        given:
        def graph = singleSlotGroupGraph(TypeUniverse.STRING, TypeUniverse.STRING)

        // Track all bridge invocations
        final invocations = []
        def mockBridge = new Bridge() {
            @Override
            Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
                invocations << [from: from, to: to]
                Stream.empty()
            }
        }

        when:
        ExpansionHarness.expand(graph, List.of(mockBridge), [])

        then:
        // Every invocation must have STRING as outputType (to)
        // In the target-driven model, the frontier type (STRING) is passed as outputType
        !invocations.empty
        invocations.every { inv ->
            TypeUniverse.types().isSameType(inv.to, TypeUniverse.STRING)
        }
    }

    def 'Bridge mock never receives frontier type as inputType'() {
        given:
        def graph = singleSlotGroupGraph(TypeUniverse.INT, TypeUniverse.STRING)

        final invocations = []
        def mockBridge = new Bridge() {
            @Override
            Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
                invocations << [from: from, to: to]
                Stream.empty()
            }
        }

        when:
        ExpansionHarness.expand(graph, List.of(mockBridge), [])

        then:
        // The frontier is STRING (target type). It should NEVER appear as inputType (from).
        invocations.every { inv ->
            !TypeUniverse.types().isSameType(inv.from, TypeUniverse.STRING)
        }
    }

    private static MapperGraph singleSlotGroupGraph(srcType, tgtType) {
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def src = new Node(Optional.of(srcType), new SourceLocation(AccessPath.of('in')), scope)
        def returnRoot = new Node(Optional.of(tgtType), new TargetLocation(TargetPath.of('')), scope)
        def slot = new Node(Optional.of(tgtType), new TargetLocation(TargetPath.of('out')), scope)
        graph.addNode(src)
        graph.addNode(returnRoot)
        graph.addNode(slot)
        final realisedEdge = Edge.realised(slot, returnRoot, 1, { vars, inputs -> com.palantir.javapoet.CodeBlock.of('') }, 'test.GroupTarget')
        graph.addEdge(realisedEdge)
        graph.addEdge(Edge.seedForTest(src, slot))
        graph.addGroup(ExpansionGroup.of(
                returnRoot,
                [slot],
                { vars, inputs -> com.palantir.javapoet.CodeBlock.of('') } as GroupCodegen,
                'test.GroupTarget',
                Set.of(realisedEdge),
                graph))
        graph
    }
}
