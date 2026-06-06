package io.github.joke.percolate.processor.stages.expand

import io.github.joke.percolate.processor.test.TestGroups

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver
import io.github.joke.percolate.processor.nullability.NullabilityAnnotations
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.CombinatorialMatch
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.ExpansionStep
import io.github.joke.percolate.spi.Slot
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.HarnessResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * Behaviour of {@link DirectiveBindingExpander} under the unified SPI: the source slot is resolved first, then the
 * target root is produced from the in-view candidates via the strategy round. A same-type match folds a NOOP
 * CONVERSION ({@link DirectAssign}) edge into the group; a type mismatch with no producer emits nothing. The root's
 * declared type is the pinned expected type — never the source slot's type. SAT (empty pending) only once the root
 * has a producing edge in view or a SAT child; the fold itself is emitted as a delta and the root stays pending in
 * the same pass.
 */
@Tag('unit')
class DirectiveBindingExpanderSpec extends Specification {

    private static final EdgeCodegen EDGE_NOOP = { vars, inputs -> CodeBlock.of('') }

    def graph = new MapperGraph()
    def scope = new HarnessScope('m()')
    def ctx = HarnessResolveCtx.create()
    def resolver = new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), TypeUniverse.elements())
    def state = new ExpansionStateImpl(graph, new Applier(resolver), ctx)
    def frontierMatcher = new FrontierMatcher([sameTypeAssign()], new InputAllocator(ctx), ctx)
    def slotResolver = new SlotResolver(frontierMatcher)
    @Subject
    DirectiveBindingExpander expander = new DirectiveBindingExpander(slotResolver, frontierMatcher)

    def 'stays pending without typing the root when the declared target type is unknown'() {
        given:
        def group = bindingGroup(TypeUniverse.STRING, null)

        when:
        def result = expander.step(group, state)

        then:
        result.pendingSlots == [group.root]
        result.bundles.collectMany { it.deltas }.findAll { it instanceof TypeNode && it.node.is(group.root) }.empty
    }

    def 'a same-type binding folds a NOOP direct-assign edge into the already-typed root'() {
        given:
        def group = bindingGroup(TypeUniverse.STRING, null)
        pinTargetType(group, TypeUniverse.STRING)

        when:
        def result = expander.step(group, state)

        then: 'the root has a producing edge emitted; SAT lands the next pass once the fold is applied'
        result.pendingSlots == [group.root]
        def deltas = result.bundles.collectMany { it.deltas }
        def edges = deltas.findAll { it instanceof AddEdge }
        edges.size() == 1
        edges[0].from.is(group.inputs()[0])
        edges[0].to.is(group.root)
        edges[0].edge.weight == Weights.NOOP

        and: 'the root was already typed (by its parent constructor in the real flow), so the fold emits no TypeNode'
        deltas.findAll { it instanceof TypeNode && it.node.is(group.root) }.empty
    }

    def 'never stamps the source slot type onto the root'() {
        given:
        def group = bindingGroup(TypeUniverse.STRING, null)
        pinTargetType(group, TypeUniverse.LONG)

        when:
        def result = expander.step(group, state)

        then: 'no producer exists for the LONG root, so the STRING source type is never folded in'
        result.bundles.collectMany { it.deltas }
                .findAll { it instanceof TypeNode && it.node.is(group.root) }
                .every { TypeUniverse.types().isSameType(it.type, TypeUniverse.LONG) }
    }

    def 'differing types with no producer emit no edge and leave the root pending'() {
        given:
        def group = bindingGroup(TypeUniverse.STRING, null)
        pinTargetType(group, TypeUniverse.LONG)

        when:
        def result = expander.step(group, state)

        then:
        result.bundles.collectMany { it.deltas }.findAll { it instanceof AddEdge }.empty
        result.pendingSlots == [group.root]
    }

    def 'differing types SAT via a SAT child sub-group without emitting a direct-assign edge'() {
        given:
        def group = bindingGroup(TypeUniverse.STRING, null)
        pinTargetType(group, TypeUniverse.LONG)
        def childSlot = new Node(Optional.empty(), new ElementLocation('element'), scope)
        graph.addNode(childSlot)
        def child = TestGroups.of(group.root, [childSlot], 'test.Child', [].toSet(), graph)
        state.markSat(child)

        when:
        def result = expander.step(group, state)

        then:
        result.pendingSlots.empty
        result.bundles.collectMany { it.deltas }.findAll { it instanceof AddEdge }.empty
    }

    /** A same-type identity fold, like the builtin {@code DirectAssign}: a NOOP CONVERSION when from == to. */
    private static CombinatorialMatch sameTypeAssign() {
        { from, to, c ->
            TypeUniverse.types().isSameType(from, to)
                    ? Stream.of(ExpansionStep.conversion(new Slot('value', from, Weights.NOOP, null), to, EDGE_NOOP, Weights.NOOP))
                    : Stream.empty()
        } as CombinatorialMatch
    }

    // In the dissolved model the directive-binding root is typed directly (its parent constructor types the leaf at
    // bind time); effectiveTypeFor reads node.getType(), so there is no separate "expected type" pin.
    private void pinTargetType(final ExpansionGroup group, final TypeMirror targetType) {
        group.root.setTyping(targetType, io.github.joke.percolate.spi.Nullability.UNKNOWN)
    }

    private ExpansionGroup bindingGroup(final TypeMirror slotType, final TypeMirror rootType) {
        def root = new Node(Optional.ofNullable(rootType), new TargetLocation(TargetPath.of('out')), scope)
        def slot = new Node(Optional.ofNullable(slotType), new SourceLocation(AccessPath.of('p')), scope)
        graph.addNode(root)
        graph.addNode(slot)
        TestGroups.of(root, [slot], 'io.github.joke.percolate.processor.stages.seed.SeedStage', [].toSet(), graph)
    }
}
