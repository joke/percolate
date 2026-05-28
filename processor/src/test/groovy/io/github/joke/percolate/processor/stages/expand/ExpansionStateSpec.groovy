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

@Tag('unit')
class ExpansionStateSpec extends Specification {

    private static final GroupCodegen GROUP_NOOP = { vars, inputs -> CodeBlock.of('') }

    def graph = new MapperGraph()
    def scope = new HarnessScope('m()')
    def resolver = new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), TypeUniverse.elements())
    def applier = new Applier(resolver)
    @Subject
    ExpansionStateImpl state = new ExpansionStateImpl(graph, applier, HarnessResolveCtx.create())

    def 'viewOf returns a read-only graph that rejects mutation'() {
        given:
        def root = source('r', TypeUniverse.STRING)
        def slot = source('s', TypeUniverse.STRING)
        graph.addNode(root)
        graph.addNode(slot)
        def group = ExpansionGroup.of(root, [slot], GROUP_NOOP, 'test.G', [].toSet(), graph)
        def stray = source('stray', TypeUniverse.STRING)
        graph.addNode(stray)

        when:
        state.viewOf(group).addVertex(stray)

        then:
        thrown(UnsupportedOperationException)
    }

    def 'markSat flips isSat for the group'() {
        given:
        def root = target('r', TypeUniverse.STRING)
        def slot = target('s', TypeUniverse.STRING)
        graph.addNode(root)
        graph.addNode(slot)
        def group = ExpansionGroup.of(root, [slot], GROUP_NOOP, 'test.G', [].toSet(), graph)

        expect:
        !state.isSat(group)

        when:
        state.markSat(group)

        then:
        state.isSat(group)
    }

    def 'effectiveTypeFor falls back to the slot expected type when the node is untyped'() {
        given:
        def root = target('r', TypeUniverse.STRING)
        def slot = new Node(Optional.empty(), new ElementLocation('name'), scope, Optional.of(root))
        graph.addNode(root)
        graph.addNode(slot)
        def meta = [(slot): new Slot('name', TypeUniverse.STRING, 1, TypeUniverse.anyConstruct())]
        def group = ExpansionGroup.of(root, [slot], GROUP_NOOP, 'test.G', [].toSet(), graph, meta)

        expect:
        state.typeOf(slot).empty
        TypeUniverse.types().isSameType(state.effectiveTypeFor(slot, group), TypeUniverse.STRING)
    }

    def 'producerScopeOf reads the scope the applier recorded at typing'() {
        given:
        def node = new Node(Optional.empty(), new SourceLocation(AccessPath.of('u')), scope)
        graph.addNode(node)
        def producer = TypeUniverse.anyConstruct() as javax.lang.model.element.Element
        applier.apply(state, [new DeltaBundle('test', [new TypeNode(node, TypeUniverse.STRING, producer)])])

        expect:
        state.producerScopeOf(node).is(producer)
    }

    def 'recordOutcomes records unsatNoPlan when converged with a pending group'() {
        given:
        def root = target('r', TypeUniverse.STRING)
        def slot = target('s', TypeUniverse.STRING)
        graph.addNode(root)
        graph.addNode(slot)
        def group = ExpansionGroup.of(root, [slot], GROUP_NOOP, 'test.G', [].toSet(), graph)
        graph.addGroup(group)
        state.recordPending(group, [slot])

        when:
        state.recordOutcomes(true)

        then:
        def outcomes = graph.groupOutcomes().collect()
        outcomes.size() == 1
        outcomes[0].kind == GroupOutcome.Kind.UNSAT_NO_PLAN
        outcomes[0].failingSlot.get().is(slot)
    }

    def 'recordOutcomes records unsatDidNotConverge when the budget tripped'() {
        given:
        def root = target('r', TypeUniverse.STRING)
        def slot = target('s', TypeUniverse.STRING)
        graph.addNode(root)
        graph.addNode(slot)
        def group = ExpansionGroup.of(root, [slot], GROUP_NOOP, 'test.G', [].toSet(), graph)
        graph.addGroup(group)
        state.recordPending(group, [slot])

        when:
        state.recordOutcomes(false)

        then:
        graph.groupOutcomes().collect()[0].kind == GroupOutcome.Kind.UNSAT_DID_NOT_CONVERGE
    }

    private Node source(final String path, final javax.lang.model.type.TypeMirror type) {
        new Node(Optional.of(type), new SourceLocation(AccessPath.of(path)), scope)
    }

    private Node target(final String path, final javax.lang.model.type.TypeMirror type) {
        new Node(Optional.of(type), new TargetLocation(TargetPath.of(path)), scope)
    }
}
