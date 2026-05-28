package io.github.joke.percolate.processor.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver
import io.github.joke.percolate.processor.nullability.NullabilityAnnotations
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.GroupCodegen
import io.github.joke.percolate.spi.Nullability
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

    def 'propagates the source slot type onto an untyped target root'() {
        given:
        def group = bindingGroup(TypeUniverse.STRING, null)

        when:
        def result = expander.step(group, state)

        then:
        result.pendingSlots == [group.root]
        def typings = result.bundles.collectMany { it.deltas }.findAll { it instanceof TypeNode }
        typings.size() == 1
        typings[0].node.is(group.root)
        TypeUniverse.types().isSameType(typings[0].type, TypeUniverse.STRING)
    }

    def 'emits a direct-assign edge and SATs once the types match'() {
        given:
        def group = bindingGroup(TypeUniverse.STRING, null)
        group.root.setTyping(TypeUniverse.STRING, Nullability.UNKNOWN)

        when:
        def result = expander.step(group, state)

        then:
        result.pendingSlots.empty
        def edges = result.bundles.collectMany { it.deltas }.findAll { it instanceof AddEdge }
        edges.size() == 1
        edges[0].edge.from.is(group.slots[0])
        edges[0].edge.to.is(group.root)
        result.bundles.collectMany { it.deltas }.any { it instanceof AddEdgeToView }
    }

    def 'leaves the slot pending when types differ and no producer exists'() {
        given:
        def group = bindingGroup(TypeUniverse.STRING, TypeUniverse.LONG)

        when:
        def result = expander.step(group, state)

        then:
        result.bundles.empty
        result.pendingSlots == [group.slots[0]]
    }

    private ExpansionGroup bindingGroup(final TypeMirror slotType, final TypeMirror rootType) {
        def root = new Node(Optional.ofNullable(rootType), new TargetLocation(TargetPath.of('out')), scope)
        def slot = new Node(Optional.ofNullable(slotType), new SourceLocation(AccessPath.of('p')), scope)
        graph.addNode(root)
        graph.addNode(slot)
        ExpansionGroup.of(root, [slot], GROUP_NOOP, 'test.DirectiveBinding', [].toSet(), graph)
    }
}
