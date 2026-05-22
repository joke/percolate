package io.github.joke.percolate.processor.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.AccessPath
import io.github.joke.percolate.processor.graph.Edge
import io.github.joke.percolate.processor.graph.EdgeKind
import io.github.joke.percolate.processor.graph.ElementLocation
import io.github.joke.percolate.processor.graph.ExpansionGroup
import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.processor.graph.Node
import io.github.joke.percolate.processor.graph.SourceLocation
import io.github.joke.percolate.processor.graph.TargetLocation
import io.github.joke.percolate.processor.graph.TargetPath
import io.github.joke.percolate.processor.stages.expand.properties.fakes.NoOpBridge
import io.github.joke.percolate.processor.test.ExpansionHarness
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.Bridge
import io.github.joke.percolate.spi.BridgeStep
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.GroupCodegen
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.ScopeTransition
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

@Tag('unit')
@Timeout(30)
class ScopeAwareAllocationSpec extends Specification {

    private static final GroupCodegen NOOP_GROUP = { vars, inputs -> CodeBlock.of('') }
    private static final EdgeCodegen NOOP_EDGE = { vars, inputs -> CodeBlock.of('') }
    private static final String ELEMENT = 'element'

    def 'ENTERING bridge matches an existing same-element-scope candidate (flatMap)'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def src = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('p')), scope)
        def elemX = new Node(Optional.of(TypeUniverse.STRING), new ElementLocation(ELEMENT), scope)
        def elemY = new Node(Optional.of(TypeUniverse.INTEGER), new ElementLocation(ELEMENT), scope)
        def root = new Node(Optional.of(TypeUniverse.INTEGER), new TargetLocation(TargetPath.of('')), scope)
        graph.addNode(src)
        graph.addNode(elemX)
        graph.addNode(elemY)
        graph.addNode(root)
        graph.addEdge(Edge.realised(src, elemX, 1, NOOP_EDGE, 'test.Seed'))
        def slotEdge = Edge.realised(elemY, root, 1, NOOP_EDGE, 'test.GroupTarget')
        graph.addEdge(slotEdge)
        graph.addGroup(ExpansionGroup.of(root, [elemY], NOOP_GROUP, 'test.GroupTarget', [slotEdge].toSet(), graph))

        when:
        def bridge = new ScopeAwareBridge(TypeUniverse.STRING, TypeUniverse.INTEGER, ScopeTransition.ENTERING, ELEMENT)
        def result = ExpansionHarness.expand(graph, List.of(bridge), List.of())
        def expanded = result.expandedGraph()

        then: 'same-elem flatMap edge elemX → elemY emitted; no fresh element-scope node allocated'
        def elemXToY = expanded.edges().filter {
            it.kind == EdgeKind.REALISED && it.from.is(elemX) && it.to.is(elemY)
        }.toList()
        elemXToY.size() == 1

        and: 'still only one elem:String node in the graph'
        expanded.nodes().filter {
            it.loc instanceof ElementLocation
            && (it.loc as ElementLocation).role == ELEMENT
            && it.type.isPresent()
            && TypeUniverse.types().isSameType(it.type.get(), TypeUniverse.STRING)
        }.count() == 1L
    }

    def 'ENTERING bridge prefers regular-scope candidate when no same-scope candidate exists'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def src = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('p')), scope)
        def elemY = new Node(Optional.of(TypeUniverse.INTEGER), new ElementLocation(ELEMENT), scope)
        def root = new Node(Optional.of(TypeUniverse.INTEGER), new TargetLocation(TargetPath.of('')), scope)
        graph.addNode(src)
        graph.addNode(elemY)
        graph.addNode(root)
        def slotEdge = Edge.realised(elemY, root, 1, NOOP_EDGE, 'test.GroupTarget')
        graph.addEdge(slotEdge)
        graph.addGroup(ExpansionGroup.of(root, [elemY], NOOP_GROUP, 'test.GroupTarget', [slotEdge].toSet(), graph))

        when:
        def bridge = new ScopeAwareBridge(TypeUniverse.STRING, TypeUniverse.INTEGER, ScopeTransition.ENTERING, ELEMENT)
        def result = ExpansionHarness.expand(graph, List.of(bridge), List.of())
        def expanded = result.expandedGraph()

        then: 'edge from the regular-scope source node directly to elemY; no fresh element-scope String node'
        def srcToElemY = expanded.edges().filter {
            it.kind == EdgeKind.REALISED && it.from.is(src) && it.to.is(elemY)
        }.toList()
        srcToElemY.size() == 1

        and: 'no fresh element-scope String node was allocated'
        expanded.nodes().filter {
            it.loc instanceof ElementLocation
            && it.type.isPresent()
            && TypeUniverse.types().isSameType(it.type.get(), TypeUniverse.STRING)
        }.count() == 0L
    }

    def 'EXITING bridge allocates input at ElementLocation when no element candidate exists'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def src = new Node(Optional.of(TypeUniverse.LONG_TYPE), new SourceLocation(AccessPath.of('p')), scope)
        def slot = new Node(Optional.of(TypeUniverse.INTEGER), new TargetLocation(TargetPath.of('out')), scope)
        def root = new Node(Optional.of(TypeUniverse.INTEGER), new TargetLocation(TargetPath.of('')), scope)
        graph.addNode(src)
        graph.addNode(slot)
        graph.addNode(root)
        def slotEdge = Edge.realised(slot, root, 1, NOOP_EDGE, 'test.GroupTarget')
        graph.addEdge(slotEdge)
        graph.addGroup(ExpansionGroup.of(root, [slot], NOOP_GROUP, 'test.GroupTarget', [slotEdge].toSet(), graph))

        when:
        def bridge = new ScopeAwareBridge(TypeUniverse.STRING, TypeUniverse.INTEGER, ScopeTransition.EXITING, ELEMENT)
        def result = ExpansionHarness.expand(graph, List.of(bridge), List.of())
        def expanded = result.expandedGraph()

        then: 'a fresh element-scope String node was allocated'
        def freshElemNodes = expanded.nodes().filter {
            it.loc instanceof ElementLocation
            && (it.loc as ElementLocation).role == ELEMENT
            && it.type.isPresent()
            && TypeUniverse.types().isSameType(it.type.get(), TypeUniverse.STRING)
        }.toList()
        freshElemNodes.size() == 1

        and: 'the REALISED edge goes from the fresh element-scope node to the slot'
        def freshElem = freshElemNodes.first()
        def edgeFromFreshElem = expanded.edges().filter {
            it.kind == EdgeKind.REALISED && it.from.is(freshElem) && it.to.is(slot)
        }.toList()
        edgeFromFreshElem.size() == 1
    }

    def 'EXITING bridge reuses an existing element-scope candidate'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def src = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('p')), scope)
        def elemX = new Node(Optional.of(TypeUniverse.STRING), new ElementLocation(ELEMENT), scope)
        def slot = new Node(Optional.of(TypeUniverse.INTEGER), new TargetLocation(TargetPath.of('out')), scope)
        def root = new Node(Optional.of(TypeUniverse.INTEGER), new TargetLocation(TargetPath.of('')), scope)
        graph.addNode(src)
        graph.addNode(elemX)
        graph.addNode(slot)
        graph.addNode(root)
        graph.addEdge(Edge.realised(src, elemX, 1, NOOP_EDGE, 'test.Seed'))
        def slotEdge = Edge.realised(slot, root, 1, NOOP_EDGE, 'test.GroupTarget')
        graph.addEdge(slotEdge)
        graph.addGroup(ExpansionGroup.of(root, [slot], NOOP_GROUP, 'test.GroupTarget', [slotEdge].toSet(), graph))

        when:
        def bridge = new ScopeAwareBridge(TypeUniverse.STRING, TypeUniverse.INTEGER, ScopeTransition.EXITING, ELEMENT)
        def result = ExpansionHarness.expand(graph, List.of(bridge), List.of())
        def expanded = result.expandedGraph()

        then: 'no fresh element-scope String node was allocated; the existing elemX is reused'
        expanded.nodes().filter {
            it.loc instanceof ElementLocation
            && (it.loc as ElementLocation).role == ELEMENT
            && it.type.isPresent()
            && TypeUniverse.types().isSameType(it.type.get(), TypeUniverse.STRING)
        }.count() == 1L

        and: 'REALISED edge from existing elemX directly to slot'
        def elemXToSlot = expanded.edges().filter {
            it.kind == EdgeKind.REALISED && it.from.is(elemX) && it.to.is(slot)
        }.toList()
        elemXToSlot.size() == 1
    }

    def 'no-op bridge with PRESERVING default does not interact with element scope'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def src = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('p')), scope)
        def root = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('')), scope)
        graph.addNode(src)
        graph.addNode(root)
        graph.addGroup(ExpansionGroup.of(root, [], NOOP_GROUP, 'test.GroupTarget', [].toSet(), graph))

        expect:
        def result = ExpansionHarness.expand(graph, List.of(new NoOpBridge()), List.of())
        result != null
    }

    private static final class ScopeAwareBridge implements Bridge {
        private static final EdgeCodegen NO_OP = { vars, inputs -> CodeBlock.of('') }
        private final TypeMirror inType
        private final TypeMirror outType
        private final ScopeTransition transition
        private final String role

        ScopeAwareBridge(
                final TypeMirror inType,
                final TypeMirror outType,
                final ScopeTransition transition,
                final String role) {
            this.inType = inType
            this.outType = outType
            this.transition = transition
            this.role = role
        }

        @Override
        Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
            if (!ctx.types().isSameType(to, outType)) {
                return Stream.empty()
            }
            Stream.of(new BridgeStep(inType, outType, Weights.STEP, NO_OP, transition, role))
        }
    }
}
