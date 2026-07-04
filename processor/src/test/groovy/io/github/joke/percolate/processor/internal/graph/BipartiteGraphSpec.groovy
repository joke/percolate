package io.github.joke.percolate.processor.internal.graph

import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import org.jgrapht.Graph
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class BipartiteGraphSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()

    final MapperGraph graph = new MapperGraph()
    final Scope scope = new HarnessScope('m()')

    // ---- Value identity and dedup (design D4) ---------------------------------------------------

    def 'type-identical demands at one location dedup to a single shared Value'() {
        given:
        final var loc = new SourceLocation(AccessPath.of('street'))

        when:
        final var first = graph.valueFor(scope, loc, javac.STRING, Nullability.NON_NULL)
        final var second = graph.valueFor(scope, loc, javac.STRING, Nullability.NON_NULL)

        then:
        first.is(second)
    }

    def 'type-divergent demands at one location are distinct Values'() {
        given:
        final var loc = new SourceLocation(AccessPath.of('number'))

        expect:
        !graph.valueFor(scope, loc, javac.INT, Nullability.NON_NULL)
                .is(graph.valueFor(scope, loc, javac.LONG, Nullability.NON_NULL))
    }

    def 'nullness-divergent demands at one location are distinct Values'() {
        given:
        final var loc = new SourceLocation(AccessPath.of('name'))

        expect:
        !graph.valueFor(scope, loc, javac.STRING, Nullability.NON_NULL)
                .is(graph.valueFor(scope, loc, javac.STRING, Nullability.NULLABLE))
    }

    // ---- Write-once typing ----------------------------------------------------------------------

    def 'an untyped Value accepts typing exactly once'() {
        given:
        final var value = new Value(new SourceLocation(AccessPath.of('x')), scope, Optional.empty(), Optional.empty())

        when:
        value.setTyping(javac.STRING, Nullability.NON_NULL)

        then:
        value.type.get() == javac.STRING
        value.nullness.get() == Nullability.NON_NULL
    }

    def 'typing an already-typed Value is rejected'() {
        given:
        final var value = new Value(new SourceLocation(AccessPath.of('x')), scope, Optional.empty(), Optional.empty())
        value.setTyping(javac.STRING, Nullability.NON_NULL)

        when:
        value.setTyping(javac.INTEGER, Nullability.NULLABLE)

        then:
        thrown(IllegalStateException)
    }

    def 'a Value typed at construction rejects setTyping'() {
        given:
        final var value = new Value(
                new SourceLocation(AccessPath.of('x')), scope,
                Optional.of(javac.STRING), Optional.of(Nullability.NON_NULL))

        when:
        value.setTyping(javac.STRING, Nullability.NON_NULL)

        then:
        thrown(IllegalStateException)
    }

    // ---- Atomic AddOperation (design D1, D3) ----------------------------------------------------

    def 'AddOperation lands the Operation with one output edge and one inbound edge per port'() {
        given:
        final var op = graph.apply(constructor('addr', [port('number', javac.INT), port('street', javac.STRING)]))
        final var view = graph.bipartiteView()

        expect:
        view.outgoingEdgesOf(op).size() == 1
        view.incomingEdgesOf(op).size() == 2
        view.incomingEdgesOf(op).collect { it.portId.get() }.toSet() == ['number', 'street'].toSet()
    }

    def 'a landed Operation carries the spec label and exposes no strategy FQN'() {
        given:
        final var op = graph.apply(constructor('addr', [port('street', javac.STRING)]))

        expect:
        op.label == 'new addr'
        Operation.declaredFields.every { it.name != 'strategyFqn' }
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
        final var x = leaf('x', javac.INT)
        final var op = graph.apply(new AddOperation('new Range', Stub(Codegen), 1, false,
                [new PortBinding(new Port('low', javac.INT, Nullability.NON_NULL), x),
                 new PortBinding(new Port('high', javac.INT, Nullability.NON_NULL), x)],
                target('range', javac.STRING), Optional.empty()))
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
        final var c1 = graph.apply(new AddOperation('C1', Stub(Codegen), 1, false,
                [port('number', javac.INT), port('street', javac.STRING)],
                target('addr', javac.STRING), Optional.empty()))
        final var c2 = graph.apply(new AddOperation('C2', Stub(Codegen), 2, false,
                [port('number', javac.LONG), port('street', javac.STRING)],
                target('addr', javac.STRING), Optional.empty()))
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
        final var crossing = new AddOperation('cross', Stub(Codegen), 1, false,
                [new PortBinding(new Port('p', javac.STRING, Nullability.NON_NULL),
                        new AddValue(other, new SourceLocation(AccessPath.of('p')), javac.STRING, Nullability.NON_NULL))],
                target('out', javac.STRING), Optional.empty())

        when:
        graph.apply(crossing)

        then:
        thrown(IllegalStateException)
    }

    def 'a scope-owning Operation mints its child return-root eagerly and declares its element input lazily'() {
        given:
        final var decl = new ChildScopeDecl(
                javac.INTEGER, Nullability.NON_NULL, javac.STRING, Nullability.NON_NULL)
        final var op = graph.apply(new AddOperation('map', Stub(Codegen), 1, false,
                [port('src', javac.LIST_OF_INT)],
                target('out', javac.LIST_OF_STRING), Optional.of(decl)))
        final var child = op.childScope.get()

        expect: 'the return-root Value is minted eagerly inside the child scope'
        op.childScope.present
        child.returnRoot.type.get() == javac.STRING
        child.returnRoot.scope.is(child)

        and: 'the element input is declared (type INTEGER at an ElementLocation), not minted as a Value'
        child.elementInput.type == javac.INTEGER
        child.elementInput.nullness == Nullability.NON_NULL
        child.elementInput.location instanceof ElementLocation

        and: 'no element param-root Value exists until a port reuses it'
        graph.valuesIn(child).noneMatch { it.loc instanceof ElementLocation }
    }

    def 'scopeView masks out every vertex that lives in another scope'() {
        final var other = new HarnessScope('other()')
        final var inScope = graph.valueFor(scope, new SourceLocation(AccessPath.of('a')), javac.STRING,
                Nullability.NON_NULL)
        final var elsewhere = graph.valueFor(other, new SourceLocation(AccessPath.of('b')), javac.STRING,
                Nullability.NON_NULL)

        when:
        final var view = graph.scopeView(scope)

        then:
        view.vertexSet().contains(inScope)
        !view.vertexSet().contains(elsewhere)
    }

    // ---- Vertex / edge identity ----------------------------------------------------------------

    def 'a Dep edge is equal only to itself'() {
        final var dep = Dep.port('p')

        expect:
        dep == dep
        dep != Dep.port('p')
        dep != Dep.output()
    }

    def 'an Operation vertex is equal only to itself'() {
        final var first = graph.apply(constructor('a', [port('x', javac.STRING)]))
        final var second = graph.apply(constructor('b', [port('y', javac.STRING)]))

        expect:
        first == first
        first != second
    }

    def 'an uninitialised child scope rejects access to its roots until the owning Operation lands'() {
        final var op = new Operation(0, 'map', Stub(Codegen), 1, false, [], scope, true)
        final var child = op.childScope.get()

        when:
        child.returnRoot

        then:
        thrown(NullPointerException)

        when:
        child.elementInput

        then:
        thrown(NullPointerException)
    }

    def 'initialising an already-landed child scope a second time is rejected'() {
        final var decl = new ChildScopeDecl(
                javac.INTEGER, Nullability.NON_NULL, javac.STRING, Nullability.NON_NULL)
        final var op = graph.apply(new AddOperation('map', Stub(Codegen), 1, false,
                [port('src', javac.LIST_OF_INT)],
                target('out', javac.LIST_OF_STRING), Optional.of(decl)))
        final var child = op.childScope.get()

        when: 're-initialising with the roots already minted at landing time'
        child.initialise(child.returnRoot, child.elementInput)

        then:
        thrown(IllegalStateException)
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
        new AddOperation('new ' + outputSlot, Stub(Codegen), 1, false, ports,
                target(outputSlot, javac.STRING), Optional.empty())
    }
}
