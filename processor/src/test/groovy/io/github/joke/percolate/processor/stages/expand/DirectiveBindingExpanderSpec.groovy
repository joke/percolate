package io.github.joke.percolate.processor.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver
import io.github.joke.percolate.processor.nullability.NullabilityAnnotations
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.GroupCodegen
import io.github.joke.percolate.spi.Slot
import io.github.joke.percolate.spi.test.HarnessResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class DirectiveBindingExpanderSpec extends Specification {

    private static final GroupCodegen GROUP_NOOP = { vars, inputs -> CodeBlock.of('') }

    def graph = new MapperGraph()
    def scope = new HarnessScope('m()')
    def ctx = HarnessResolveCtx.create()
    def resolver = new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), TypeUniverse.elements())
    def state = new ExpansionStateImpl(graph, new Applier(resolver), ctx)
    def slotResolver = new SlotResolver(new FrontierMatcher([], new InputAllocator(ctx), ctx), [], ctx)
    @Subject
    DirectiveBindingExpander expander = new DirectiveBindingExpander(slotResolver, ctx)

    def 'stays pending without typing the root when the declared target type is unknown'() {
        given:
        def group = bindingGroup(TypeUniverse.STRING, null)

        when:
        def result = expander.step(group, state)

        then:
        result.pendingSlots == [group.root]
        result.bundles.collectMany { it.deltas }.findAll { it instanceof TypeNode && it.node.is(group.root) }.empty
    }

    def 'types the root with the target type and emits a NOOP direct-assign edge then SATs when source and target match'() {
        given:
        def group = bindingGroup(TypeUniverse.STRING, null)
        pinTargetType(group, TypeUniverse.STRING)

        when:
        def result = expander.step(group, state)

        then:
        result.pendingSlots.empty
        def deltas = result.bundles.collectMany { it.deltas }
        def edges = deltas.findAll { it instanceof AddEdge }
        edges.size() == 1
        edges[0].edge.from.is(group.slots[0])
        edges[0].edge.to.is(group.root)
        edges[0].edge.weight == Weights.NOOP
        deltas.any { it instanceof AddEdgeToView }
        def typings = deltas.findAll { it instanceof TypeNode && it.node.is(group.root) }
        typings.size() == 1
        TypeUniverse.types().isSameType(typings[0].type, TypeUniverse.STRING)
    }

    def 'never stamps the source slot type onto the root'() {
        given:
        def group = bindingGroup(TypeUniverse.STRING, null)
        pinTargetType(group, TypeUniverse.LONG)

        when:
        def result = expander.step(group, state)

        then:
        result.bundles.collectMany { it.deltas }
                .findAll { it instanceof TypeNode && it.node.is(group.root) }
                .every { TypeUniverse.types().isSameType(it.type, TypeUniverse.LONG) }
    }

    def 'differing types with no producer emit no direct-assign edge and leave the slot pending'() {
        given:
        def group = bindingGroup(TypeUniverse.STRING, null)
        pinTargetType(group, TypeUniverse.LONG)

        when:
        def result = expander.step(group, state)

        then:
        result.bundles.collectMany { it.deltas }.findAll { it instanceof AddEdge }.empty
        result.pendingSlots == [group.slots[0]]
    }

    def 'differing types SAT via a SAT child sub-group without emitting a direct-assign edge'() {
        given:
        def group = bindingGroup(TypeUniverse.STRING, null)
        pinTargetType(group, TypeUniverse.LONG)
        def childSlot = new Node(Optional.empty(), new ElementLocation('element'), scope)
        graph.addNode(childSlot)
        def child = ExpansionGroup.of(group.root, [childSlot], GROUP_NOOP, 'test.Child', [].toSet(), graph)
        graph.addGroup(child)
        state.markSat(child)

        when:
        def result = expander.step(group, state)

        then:
        result.pendingSlots.empty
        result.bundles.collectMany { it.deltas }.findAll { it instanceof AddEdge }.empty
    }

    private void pinTargetType(final ExpansionGroup group, final TypeMirror targetType) {
        group.recordExpectedType(group.root, new Slot('out', targetType, 1, TypeUniverse.anyConstruct()))
    }

    private ExpansionGroup bindingGroup(final TypeMirror slotType, final TypeMirror rootType) {
        def root = new Node(Optional.ofNullable(rootType), new TargetLocation(TargetPath.of('out')), scope)
        def slot = new Node(Optional.ofNullable(slotType), new SourceLocation(AccessPath.of('p')), scope)
        graph.addNode(root)
        graph.addNode(slot)
        ExpansionGroup.of(root, [slot], GROUP_NOOP, 'test.DirectiveBinding', [].toSet(), graph)
    }
}
