package io.github.joke.percolate.processor.graph

import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.test.TypeUniverse
import org.jgrapht.Graph
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class BipartiteGraphSpec extends Specification {

    final MapperGraph graph = new MapperGraph()
    final Scope scope = new HarnessScope('m()')

    // ---- Value identity and dedup (design D4) ---------------------------------------------------

    def 'type-identical demands at one location dedup to a single shared Value'() {
        given:
        final var loc = new SourceLocation(AccessPath.of('street'))

        when:
        final var first = graph.valueFor(scope, loc, TypeUniverse.STRING, Nullability.NON_NULL)
        final var second = graph.valueFor(scope, loc, TypeUniverse.STRING, Nullability.NON_NULL)

        then:
        first.is(second)
    }

    def 'type-divergent demands at one location are distinct Values'() {
        given:
        final var loc = new SourceLocation(AccessPath.of('number'))

        expect:
        !graph.valueFor(scope, loc, TypeUniverse.INT, Nullability.NON_NULL)
                .is(graph.valueFor(scope, loc, TypeUniverse.LONG, Nullability.NON_NULL))
    }

    def 'nullness-divergent demands at one location are distinct Values'() {
        given:
        final var loc = new SourceLocation(AccessPath.of('name'))

        expect:
        !graph.valueFor(scope, loc, TypeUniverse.STRING, Nullability.NON_NULL)
                .is(graph.valueFor(scope, loc, TypeUniverse.STRING, Nullability.NULLABLE))
    }

    // ---- Write-once typing ----------------------------------------------------------------------

    def 'an untyped Value accepts typing exactly once'() {
        given:
        final var value = new Value(new SourceLocation(AccessPath.of('x')), scope, Optional.empty(), Optional.empty())

        when:
        value.setTyping(TypeUniverse.STRING, Nullability.NON_NULL)

        then:
        value.type.get() == TypeUniverse.STRING
        value.nullness.get() == Nullability.NON_NULL
    }

    def 'typing an already-typed Value is rejected'() {
        given:
        final var value = new Value(new SourceLocation(AccessPath.of('x')), scope, Optional.empty(), Optional.empty())
        value.setTyping(TypeUniverse.STRING, Nullability.NON_NULL)

        when:
        value.setTyping(TypeUniverse.INTEGER, Nullability.NULLABLE)

        then:
        thrown(IllegalStateException)
    }

    def 'a Value typed at construction rejects setTyping'() {
        given:
        final var value = new Value(
                new SourceLocation(AccessPath.of('x')), scope,
                Optional.of(TypeUniverse.STRING), Optional.of(Nullability.NON_NULL))

        when:
        value.setTyping(TypeUniverse.STRING, Nullability.NON_NULL)

        then:
        thrown(IllegalStateException)
    }

    // ---- Atomic AddOperation (design D1, D3) ----------------------------------------------------

    def 'AddOperation lands the Operation with one output edge and one inbound edge per port'() {
        given:
        final var op = graph.apply(constructor('addr', [port('number', TypeUniverse.INT), port('street', TypeUniverse.STRING)]))
        final var view = graph.bipartiteView()

        expect:
        view.outgoingEdgesOf(op).size() == 1
        view.incomingEdgesOf(op).size() == 2
        view.incomingEdgesOf(op).collect { it.portId.get() }.toSet() == ['number', 'street'].toSet()
    }

    def 'a zero-port Operation lands with an output edge and no inbound edges'() {
        given:
        final var op = graph.apply(constructor('FLAG', []))
        final var view = graph.bipartiteView()

        expect:
        view.outgoingEdgesOf(op).size() == 1
        view.incomingEdgesOf(op).empty
    }

    def 'one Value feeding two ports yields two distinct port-labelled edges'() {
        given:
        final var x = leaf('x', TypeUniverse.INT)
        final var op = graph.apply(new AddOperation('new Range', 'test.Strategy', Stub(Codegen), 1,
                [new PortBinding(new Port('low', TypeUniverse.INT, Nullability.NON_NULL), x),
                 new PortBinding(new Port('high', TypeUniverse.INT, Nullability.NON_NULL), x)],
                target('range', TypeUniverse.STRING), Optional.empty()))
        final var view = graph.bipartiteView()

        when:
        final var inbound = view.incomingEdgesOf(op)

        then:
        inbound.size() == 2
        inbound.collect { it.portId.get() }.toSet() == ['low', 'high'].toSet()
        inbound.collect { graph.getDepSource(it) }.toSet().size() == 1
    }

    // ---- Shared-Value overloads (design D1, D4) ------------------------------------------------

    def 'overloaded constructors share a type-identical street Value but split on a divergent number'() {
        given:
        final var c1 = graph.apply(new AddOperation('C1', 'test.Strategy', Stub(Codegen), 1,
                [port('number', TypeUniverse.INT), port('street', TypeUniverse.STRING)],
                target('addr', TypeUniverse.STRING), Optional.empty()))
        final var c2 = graph.apply(new AddOperation('C2', 'test.Strategy', Stub(Codegen), 2,
                [port('number', TypeUniverse.LONG), port('street', TypeUniverse.STRING)],
                target('addr', TypeUniverse.STRING), Optional.empty()))
        final var view = graph.bipartiteView()

        expect: 'the equal-typed street port is one shared Value, the divergent number ports are distinct'
        sourceOfPort(view, c1, 'street').is(sourceOfPort(view, c2, 'street'))
        !sourceOfPort(view, c1, 'number').is(sourceOfPort(view, c2, 'number'))

        and: 'both Operations produce the one shared output Value (an OR over its producers)'
        outputOf(view, c1).is(outputOf(view, c2))
    }

    // ---- Scope invariant (design D7) -----------------------------------------------------------

    def 'a Dep that would cross a scope boundary is rejected'() {
        given:
        final var other = new HarnessScope('other()')
        final var crossing = new AddOperation('cross', 'test.Strategy', Stub(Codegen), 1,
                [new PortBinding(new Port('p', TypeUniverse.STRING, Nullability.NON_NULL),
                        new AddValue(other, new SourceLocation(AccessPath.of('p')), TypeUniverse.STRING, Nullability.NON_NULL))],
                target('out', TypeUniverse.STRING), Optional.empty())

        when:
        graph.apply(crossing)

        then:
        thrown(IllegalStateException)
    }

    def 'a scope-owning Operation mints its child param-root and return-root inside the child scope'() {
        given:
        final var decl = new ChildScopeDecl(
                TypeUniverse.INTEGER, Nullability.NON_NULL, TypeUniverse.STRING, Nullability.NON_NULL)
        final var op = graph.apply(new AddOperation('map', 'test.Strategy', Stub(Codegen), 1,
                [port('src', TypeUniverse.LIST_OF_INT)],
                target('out', TypeUniverse.LIST_OF_STRING), Optional.of(decl)))
        final var child = op.childScope.get()

        expect:
        op.childScope.present
        child.paramRoot.type.get() == TypeUniverse.INTEGER
        child.returnRoot.type.get() == TypeUniverse.STRING
        child.paramRoot.scope.is(child)
        child.returnRoot.scope.is(child)
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static GraphVertex sourceOfPort(final Graph<GraphVertex, Dep> view, final Operation op, final String portName) {
        view.incomingEdgesOf(op).find { it.portId.get() == portName }.with { dep -> view.getEdgeSource(dep) }
    }

    private static GraphVertex outputOf(final Graph<GraphVertex, Dep> view, final Operation op) {
        view.getEdgeTarget(view.outgoingEdgesOf(op).first())
    }

    private AddValue leaf(final String slot, final TypeMirror type) {
        new AddValue(scope, new SourceLocation(AccessPath.of(slot)), type, Nullability.NON_NULL)
    }

    private AddValue target(final String slot, final TypeMirror type) {
        new AddValue(scope, new TargetLocation(TargetPath.of(slot)), type, Nullability.NON_NULL)
    }

    private PortBinding port(final String name, final TypeMirror type) {
        new PortBinding(new Port(name, type, Nullability.NON_NULL), leaf(name, type))
    }

    private AddOperation constructor(final String outputSlot, final List<PortBinding> ports) {
        new AddOperation('new ' + outputSlot, 'test.Strategy', Stub(Codegen), 1, ports,
                target(outputSlot, TypeUniverse.STRING), Optional.empty())
    }
}
