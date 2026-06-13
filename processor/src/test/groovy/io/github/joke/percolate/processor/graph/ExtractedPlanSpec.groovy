package io.github.joke.percolate.processor.graph

import io.github.joke.percolate.processor.stages.expand.HornSat
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class ExtractedPlanSpec extends Specification {

    final MapperGraph graph = new MapperGraph()
    final Scope scope = new HarnessScope('m()')
    final Value root = graph.valueFor(scope, new TargetLocation(TargetPath.of('')), TypeUniverse.STRING, Nullability.NON_NULL)

    def 'an OR resolves to the cheapest producer'() {
        given:
        final var param = source('p', TypeUniverse.STRING)
        final var cheap = produce(root, 1, [param])
        produce(root, 5, [param])

        when:
        final var plan = extract()

        then:
        plan.chosenProducer(root).get().is(cheap)
    }

    def 'an UNSAT producer never participates, regardless of weight'() {
        given:
        final var param = source('p', TypeUniverse.STRING)
        final var starved = graph.valueFor(scope, new TargetLocation(TargetPath.of('missing')), TypeUniverse.STRING, Nullability.NON_NULL)
        final var satExpensive = produce(root, 9, [param])
        produce(root, 1, [starved]) // cheaper but UNSAT (its port has no producer)

        when:
        final var plan = extract()

        then:
        plan.chosenProducer(root).get().is(satExpensive)
    }

    def 'Operation cost sums over all ports (not the minimum over one)'() {
        given: 'a two-port producer whose ports each cost 3 (sum 6), versus a one-port producer costing 5'
        final var pa = source('a', TypeUniverse.STRING)
        final var pb = source('b', TypeUniverse.STRING)
        final var ia = intermediate('ia', TypeUniverse.STRING, 3, [pa])
        final var ib = intermediate('ib', TypeUniverse.STRING, 3, [pb])
        produce(root, 0, [ia, ib]) // weight 0 + 3 + 3 = 6 (min-over-ports would be 3)
        final var ic = intermediate('ic', TypeUniverse.STRING, 1, [pa])
        final var single = produce(root, 4, [ic]) // weight 4 + 1 = 5

        when:
        final var plan = extract()

        then: 'the single-port producer (5) wins over the two-port one (6) — proving the sum'
        plan.chosenProducer(root).get().is(single)
    }

    def 'equal-cost producers select deterministically and stably'() {
        given:
        final var param = source('p', TypeUniverse.STRING)
        produce(root, 1, [param])
        produce(root, 1, [param])

        when:
        final var first = extract().chosenProducer(root).get()
        final var second = ExtractedPlan.extract(graph).chosenProducer(root).get()

        then:
        first.is(second)
    }

    def 'the losing producer subgraph remains in the underlying graph, unselected'() {
        given:
        final var param = source('p', TypeUniverse.STRING)
        produce(root, 1, [param])
        final var loser = produce(root, 5, [param])

        when:
        final var plan = extract()

        then:
        graph.producersOf(root).toList().size() == 2
        !plan.chosenProducer(root).get().is(loser)
    }

    // ---- helpers --------------------------------------------------------------------------------

    private ExtractedPlan extract() {
        HornSat.propagate(graph)
        ExtractedPlan.extract(graph)
    }

    private Value source(final String slot, final TypeMirror type) {
        graph.valueFor(scope, new SourceLocation(AccessPath.of(slot)), type, Nullability.NON_NULL)
    }

    private AddValue av(final Value value) {
        new AddValue(value.scope, value.loc, value.type.get(), value.nullness.get())
    }

    /** A producer of {@code out} with {@code weight}, fed by the given existing port-source Values. */
    private Operation produce(final Value out, final int weight, final List<Value> portSources) {
        final var ports = (0..<portSources.size()).collect { i ->
            new PortBinding(new Port('p' + i, portSources[i].type.get(), portSources[i].nullness.get()), av(portSources[i]))
        }
        graph.apply(new AddOperation('op', 'test', Stub(Codegen), weight, ports, av(out), Optional.empty()))
    }

    /** Mints an intermediate target Value of {@code type} produced with {@code weight} from {@code portSources}. */
    private Value intermediate(final String slot, final TypeMirror type, final int weight, final List<Value> portSources) {
        final var value = graph.valueFor(scope, new TargetLocation(TargetPath.of(slot)), type, Nullability.NON_NULL)
        produce(value, weight, portSources)
        value
    }
}
