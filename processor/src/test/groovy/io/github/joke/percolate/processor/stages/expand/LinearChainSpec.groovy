package io.github.joke.percolate.processor.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
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
class LinearChainSpec extends Specification {

    private static final GroupCodegen NOOP_GROUP = { vars, inputs -> CodeBlock.of('') }
    private static final EdgeCodegen NOOP_EDGE = { vars, inputs -> CodeBlock.of('') }

    def 'Set<String> from List<String> expands to a linear chain through ElementLocation (no diamond)'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def setOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.Set'), TypeUniverse.STRING)
        def src = new Node(Optional.of(TypeUniverse.LIST_OF_STRING), new SourceLocation(AccessPath.of('xs')), scope)
        def slot = new Node(Optional.of(setOfString), new TargetLocation(TargetPath.of('out')), scope)
        def root = new Node(Optional.of(setOfString), new TargetLocation(TargetPath.of('')), scope)
        graph.addNode(src)
        graph.addNode(slot)
        graph.addNode(root)
        def slotEdge = Edge.realised(slot, root, 1, NOOP_EDGE, 'test.GroupTarget')
        graph.addEdge(slotEdge)
        graph.addGroup(ExpansionGroup.of(root, [slot], NOOP_GROUP, 'test.GroupTarget', [slotEdge].toSet(), graph))

        def unwrap = new ScopeBridge(TypeUniverse.LIST_OF_STRING, TypeUniverse.STRING, ScopeTransition.ENTERING)
        def collect = new ScopeBridge(TypeUniverse.STRING, setOfString, ScopeTransition.EXITING)

        when:
        def result = ExpansionHarness.expand(graph, List.of(unwrap, collect), List.of())
        def expanded = result.expandedGraph()

        then: 'no diagnostics — the group reaches SAT'
        result.diagnostics().empty

        and: 'exactly one ElementLocation node carrying String type'
        def elemNodes = expanded.nodes()
                .filter { it.loc instanceof ElementLocation
                        && it.type.isPresent()
                        && TypeUniverse.types().isSameType(it.type.get(), TypeUniverse.STRING) }
                .toList()
        elemNodes.size() == 1
        def elemNode = elemNodes.first()

        and: 'the ElementLocation node has exactly one incoming REALISED edge from the source list'
        def incoming = expanded.edges()
                .filter { it.kind == EdgeKind.REALISED && it.to.is(elemNode) }
                .toList()
        incoming.size() == 1
        incoming.first().from.is(src)

        and: 'the ElementLocation node has exactly one outgoing REALISED edge to the target set slot'
        def outgoing = expanded.edges()
                .filter { it.kind == EdgeKind.REALISED && it.from.is(elemNode) }
                .toList()
        outgoing.size() == 1
        outgoing.first().to.is(slot)

        and: 'no parallel diamond edge from src:List<String> directly to slot:Set<String>'
        def parallel = expanded.edges()
                .filter { it.kind == EdgeKind.REALISED && it.from.is(src) && it.to.is(slot) }
                .toList()
        parallel.empty
    }

    private static final class ScopeBridge implements Bridge {
        private final TypeMirror inType
        private final TypeMirror outType
        private final ScopeTransition transition

        ScopeBridge(final TypeMirror inType, final TypeMirror outType, final ScopeTransition transition) {
            this.inType = inType
            this.outType = outType
            this.transition = transition
        }

        @Override
        Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
            if (!ctx.types().isSameType(to, outType)) {
                return Stream.empty()
            }
            Stream.of(new BridgeStep(inType, outType, Weights.CONTAINER, NOOP_EDGE, transition, 'element'))
        }
    }
}
