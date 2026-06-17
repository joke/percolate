package io.github.joke.percolate.processor.graph

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

    def 'a total producer is preferred over a cheaper partial one (totality dominates cost)'() {
        given:
        final var param = source('p', TypeUniverse.STRING)
        producePartial(root, 1, [param]) // cheaper, but may throw on a valid input
        final var total = produce(root, 9, [param]) // pricier, but never throws

        when:
        final var plan = extract()

        then:
        plan.chosenProducer(root).get().is(total)
    }

    def 'a partial producer is chosen only as the sole producer'() {
        given:
        final var param = source('p', TypeUniverse.STRING)
        final var only = producePartial(root, 1, [param])

        when:
        final var plan = extract()

        then:
        plan.chosenProducer(root).get().is(only)
    }

    def 'totality dominance counts partials transitively through ports, before cost'() {
        given: 'two total heads for root: one fed through a partial intermediate (cheap), one all-total (pricier)'
        final var pa = source('a', TypeUniverse.STRING)
        final var pb = source('b', TypeUniverse.STRING)
        final var viaPartial = intermediatePartial('ip', TypeUniverse.STRING, 0, [pa])
        final var viaTotal = intermediate('it', TypeUniverse.STRING, 8, [pb])
        produce(root, 0, [viaPartial]) // total head, but one partial in its subtree
        final var allTotal = produce(root, 0, [viaTotal]) // zero partials, but costs more

        when:
        final var plan = extract()

        then: 'the all-total plan wins despite the higher cost'
        plan.chosenProducer(root).get().is(allTotal)
    }

    def 'reachability is derived from cost: a value reachable from a supply root is reachable, an orphan is not'() {
        given:
        final var param = source('p', TypeUniverse.STRING)
        produce(root, 1, [param])
        final var orphan = graph.valueFor(scope, new TargetLocation(TargetPath.of('orphan')), TypeUniverse.STRING, Nullability.NON_NULL)

        when:
        final var plan = extract()

        then:
        plan.reachable(param)
        plan.reachable(root)
        !plan.reachable(orphan)
    }

    def 'a producerless multi-segment ACCESS source value is unreachable, not a vacuous base case'() {
        given: 'a single-segment param (LEAF) and an orphan multi-segment source value (ACCESS) with no accessor producer'
        final var param = source('p', TypeUniverse.STRING)
        final var orphanAccess = graph.valueFor(
                scope, new SourceLocation(new AccessPath(['p', 'addr'])), TypeUniverse.STRING, Nullability.NON_NULL)

        when:
        final var plan = extract()

        then: 'the LEAF param is a base case (reachable); the producerless ACCESS demand is unreachable'
        plan.reachable(param)
        !plan.reachable(orphanAccess)
    }

    def 'a zero-weight cycle is unreachable (well-foundedness: no value is reachable through its own cycle)'() {
        given: 'a and b each produced only from the other, with weight 0 and no supply root feeding the cycle'
        final var a = graph.valueFor(scope, new TargetLocation(TargetPath.of('a')), TypeUniverse.STRING, Nullability.NON_NULL)
        final var b = graph.valueFor(scope, new TargetLocation(TargetPath.of('b')), TypeUniverse.STRING, Nullability.NON_NULL)
        operation(a, 0, false, [b])
        operation(b, 0, false, [a])

        when:
        final var plan = extract()

        then:
        !plan.reachable(a)
        !plan.reachable(b)
    }

    def 'a scope-owning operation is unreachable when its child return-root has no producer'() {
        given: 'a scope-owning producer of root whose outer port is reachable but whose child return-root is never produced'
        final var param = source('p', TypeUniverse.STRING)
        graph.apply(new AddOperation('map', Stub(Codegen), 0, false,
                [new PortBinding(new Port('p0', param.type.get(), param.nullness.get()), av(param))],
                av(root),
                Optional.of(new ChildScopeDecl(TypeUniverse.STRING, Nullability.NON_NULL, TypeUniverse.STRING, Nullability.NON_NULL))))

        when:
        final var plan = extract()

        then:
        plan.reachable(param)
        !plan.reachable(root)
    }

    // ---- helpers --------------------------------------------------------------------------------

    private ExtractedPlan extract() {
        ExtractedPlan.extract(graph)
    }

    private Value source(final String slot, final TypeMirror type) {
        graph.valueFor(scope, new SourceLocation(AccessPath.of(slot)), type, Nullability.NON_NULL)
    }

    private AddValue av(final Value value) {
        new AddValue(value.scope, value.loc, value.type.get(), value.nullness.get())
    }

    /** A total producer of {@code out} with {@code weight}, fed by the given existing port-source Values. */
    private Operation produce(final Value out, final int weight, final List<Value> portSources) {
        operation(out, weight, false, portSources)
    }

    /** A partial producer (may throw on a structurally-valid input) — the totality rule deprioritises it. */
    private Operation producePartial(final Value out, final int weight, final List<Value> portSources) {
        operation(out, weight, true, portSources)
    }

    private Operation operation(final Value out, final int weight, final boolean partial, final List<Value> portSources) {
        final var ports = (0..<portSources.size()).collect { i ->
            new PortBinding(new Port('p' + i, portSources[i].type.get(), portSources[i].nullness.get()), av(portSources[i]))
        }
        graph.apply(new AddOperation('op', Stub(Codegen), weight, partial, ports, av(out), Optional.empty()))
    }

    /** Mints an intermediate target Value of {@code type} produced with {@code weight} from {@code portSources}. */
    private Value intermediate(final String slot, final TypeMirror type, final int weight, final List<Value> portSources) {
        final var value = graph.valueFor(scope, new TargetLocation(TargetPath.of(slot)), type, Nullability.NON_NULL)
        produce(value, weight, portSources)
        value
    }

    /** Like {@link #intermediate} but produced by a partial operation. */
    private Value intermediatePartial(final String slot, final TypeMirror type, final int weight, final List<Value> portSources) {
        final var value = graph.valueFor(scope, new TargetLocation(TargetPath.of(slot)), type, Nullability.NON_NULL)
        producePartial(value, weight, portSources)
        value
    }
}
