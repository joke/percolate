package io.github.joke.percolate.processor.stages.expand

import io.github.joke.percolate.processor.test.TestGroups

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.ExpansionStep
import io.github.joke.percolate.spi.ExpansionStrategy
import io.github.joke.percolate.spi.Slot
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.HarnessResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import java.util.stream.Stream

/**
 * Behaviour of the fallback {@link BridgeExpander} over a non-seed sub-group: it resolves each slot through
 * {@link SlotResolver}. A slot is SAT as a parameter-root base case (resolved against the node's own
 * {@link MethodScope}), stays pending when it has no producible type, and opens a sub-group when a BOUNDARY
 * strategy matches.
 */
@Tag('unit')
class BridgeExpanderSpec extends Specification {

    private static final EdgeCodegen EDGE_NOOP = { vars, inputs -> CodeBlock.of('') }

    def graph = new MapperGraph()
    def scope = new HarnessScope('m()')
    def ctx = HarnessResolveCtx.create()

    def 'a single-segment source slot whose own MethodScope declares the parameter is SAT'() {
        given:
        def method = singleParamMethod()
        def methodScope = new MethodScope(method)
        def paramName = method.parameters[0].simpleName.toString()
        def expander = bridgeExpander([])
        def root = new Node(Optional.of(TypeUniverse.STRING), new ElementLocation('ctor'), methodScope)
        def slot = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of(paramName)), methodScope)
        graph.addNode(root)
        graph.addNode(slot)
        def group = TestGroups.of(root, [slot], 'test.G', [].toSet(), graph)
        def state = new ExpansionStateImpl(graph, new Applier(nullResolver()))

        when:
        def result = expander.step(group, state)

        then:
        result.pendingSlots.empty
        result.bundles.empty
    }

    def 'untyped slot with no expected type stays pending without a bundle'() {
        given:
        def expander = bridgeExpander([])
        def root = new Node(Optional.of(TypeUniverse.STRING), new ElementLocation('ctor'), scope)
        def slot = new Node(Optional.empty(), new ElementLocation('x'), scope, Optional.of(root))
        graph.addNode(root)
        graph.addNode(slot)
        def group = TestGroups.of(root, [slot], 'test.G', [].toSet(), graph)
        def state = new ExpansionStateImpl(graph, new Applier(nullResolver()))

        when:
        def result = expander.step(group, state)

        then:
        result.bundles.empty
        result.pendingSlots == [slot]
    }

    def 'a matching BOUNDARY strategy spawns one sub-group and leaves the slot pending'() {
        given:
        def expander = bridgeExpander([boundaryStrategy(TypeUniverse.LONG, TypeUniverse.STRING)])
        def candidate = new Node(Optional.of(TypeUniverse.LONG), new SourceLocation(AccessPath.of('c')), scope)
        def slot = new Node(Optional.of(TypeUniverse.STRING), new ElementLocation('role'), scope)
        graph.addNode(candidate)
        graph.addNode(slot)
        def group = TestGroups.of(candidate, [slot], 'test.G', [].toSet(), graph)
        def state = new ExpansionStateImpl(graph, new Applier(nullResolver()))

        when:
        def result = expander.step(group, state)

        then:
        result.pendingSlots == [slot]
        result.bundles.size() == 1
        result.bundles[0].deltas.any { it instanceof AddGroup }
    }

    private static io.github.joke.percolate.processor.nullability.NullabilityResolver nullResolver() {
        new io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver(
                io.github.joke.percolate.processor.nullability.NullabilityAnnotations.jspecifyDefaults(),
                TypeUniverse.elements())
    }

    private static ExecutableElement singleParamMethod() {
        TypeUniverse.element('java.lang.String').enclosedElements.stream()
                .filter { it instanceof ExecutableElement }
                .map { it as ExecutableElement }
                .filter { it.simpleName.toString() == 'concat' && it.parameters.size() == 1 }
                .findFirst()
                .orElseThrow { new IllegalStateException('String.concat(String) not found') }
    }

    private static ExpansionStrategy boundaryStrategy(slotType, outType) {
        { f, c -> Stream.of(ExpansionStep.boundary([new Slot('s', slotType, Weights.STEP, null)], outType, EDGE_NOOP, Weights.STEP)) } as ExpansionStrategy
    }

    private BridgeExpander bridgeExpander(final List<ExpansionStrategy> strategies) {
        def frontierMatcher = new FrontierMatcher(strategies, new InputAllocator(ctx), ctx)
        new BridgeExpander(new SlotResolver(frontierMatcher))
    }
}
