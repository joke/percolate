package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.processor.internal.graph.AccessPath
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.Location
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.internal.graph.Scope
import io.github.joke.percolate.processor.internal.graph.SourceLocation
import io.github.joke.percolate.processor.internal.graph.Value
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver
import io.github.joke.percolate.processor.nullability.NullabilityAnnotations
import io.github.joke.percolate.processor.test.fixtures.Human
import io.github.joke.percolate.processor.test.fixtures.Person
import io.github.joke.percolate.processor.test.fixtures.PersonMapper
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.test.HarnessResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Isolated
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link SourceCandidates} seam, unit-tested directly: the in-scope source {@code Value} that can feed a demanded
 * {@link Port}, and the source types grounding unifies against. The cases isolate the {@code matchesPort} type/nullness
 * rule, the {@code matchingSource} ranking (pinned source first, then least-id graph source, then a materialised input,
 * else {@code null}), and {@code sourceTypes}.
 */
@Tag('unit')
@Isolated // shares the static TypeUniverse javac; must not run concurrently with other fixture specs (race → flaky pitest)
class SourceCandidatesSpec extends Specification {

    @Shared ResolveCtx resolveCtx = HarnessResolveCtx.create()
    @Shared JspecifyNullabilityResolver resolver =
            new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), TypeUniverse.elements())
    @Shared TypeElement personType = TypeUniverse.of(Person)

    MapperGraph graph = new MapperGraph()
    SourceCandidates candidates = new SourceCandidates(graph, new Applier(), resolver, resolveCtx)
    Scope scope = new MethodScope(methodNamed(PersonMapper, 'map'))

    def setupSpec() {
        // prime the map method's parameter/return fixture closures single-threaded before any lookup races
        // (TypeUniverse.of follows inheritance/nesting only, not method signatures — see ExpandStageDriverSpec)
        TypeUniverse.of(Human)
        TypeUniverse.of(PersonMapper)
    }

    // ---- matchesPort: same type, non-null source satisfies any nullness ---------------------------------------

    def 'matchesPort is true for an equal type and compatible nullness'() {
        expect:
        candidates.matchesPort(value(personType.asType(), source), port(personType.asType(), demanded))

        where:
        source                | demanded
        Nullability.NON_NULL  | Nullability.NON_NULL
        Nullability.NON_NULL  | Nullability.NULLABLE
        Nullability.NULLABLE  | Nullability.NULLABLE
    }

    def 'matchesPort is false when a NULLABLE source would feed a NON_NULL port'() {
        expect:
        !candidates.matchesPort(value(personType.asType(), Nullability.NULLABLE),
                port(personType.asType(), Nullability.NON_NULL))
    }

    def 'matchesPort is false for a different type regardless of nullness'() {
        expect:
        !candidates.matchesPort(value(TypeUniverse.INTEGER, Nullability.NON_NULL),
                port(personType.asType(), Nullability.NON_NULL))
    }

    // ---- matchingSource ranking -------------------------------------------------------------------------------

    def 'matchingSource prefers a matching pinned source over any in-scope candidate'() {
        given: 'a pinned Person source and a same-typed parameter that could also feed the port'
        def pinned = value(personType.asType(), Nullability.NON_NULL)

        expect:
        candidates.matchingSource(scope, port(personType.asType(), Nullability.NON_NULL), pinned).is(pinned)
    }

    def 'matchingSource ignores a pinned source that does not match, falling back to the parameter input'() {
        given: 'a pinned Integer source cannot feed a Person port'
        def pinned = value(TypeUniverse.INTEGER, Nullability.NON_NULL)

        when:
        def chosen = candidates.matchingSource(scope, port(personType.asType(), Nullability.NON_NULL), pinned)

        then: 'the Person parameter is materialised as a LEAF instead'
        !chosen.is(pinned)
        chosen.loc.role() == Location.Role.LEAF
        resolveCtx.types().isSameType(chosen.type.get(), personType.asType())
    }

    def 'matchingSource returns the least-id in-scope graph source when several match'() {
        given: 'two Person ACCESS sources already in the graph'
        def first = source(access('a', 'b'), personType.asType(), Nullability.NON_NULL)
        def second = source(access('c', 'd'), personType.asType(), Nullability.NON_NULL)

        when:
        def chosen = candidates.matchingSource(scope, port(personType.asType(), Nullability.NON_NULL), null)

        then:
        chosen == [first, second].min { it.id() }
    }

    def 'matchingSource materialises the matching parameter when no graph source exists'() {
        when:
        def chosen = candidates.matchingSource(scope, port(personType.asType(), Nullability.NON_NULL), null)

        then:
        chosen.loc instanceof SourceLocation
        chosen.loc.role() == Location.Role.LEAF
        resolveCtx.types().isSameType(chosen.type.get(), personType.asType())
    }

    def 'matchingSource returns null when nothing in scope matches the port'() {
        expect: 'no Integer source and no Integer parameter'
        candidates.matchingSource(scope, port(TypeUniverse.INTEGER, Nullability.NON_NULL), null) == null
    }

    // ---- sourceTypes ------------------------------------------------------------------------------------------

    def 'sourceTypes lists the declared parameter input types plus discovered graph sources'() {
        given:
        source(access('x', 'y'), TypeUniverse.INTEGER, Nullability.NON_NULL)

        when:
        def types = candidates.sourceTypes(scope)

        then: 'the Person parameter input and the Integer graph source are both present'
        types.any { resolveCtx.types().isSameType(it, personType.asType()) }
        types.any { resolveCtx.types().isSameType(it, TypeUniverse.INTEGER) }
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private Value value(final TypeMirror type, final Nullability nullness) {
        graph.apply(new AddValue(scope, access('leaf', 'one'), type, nullness))
    }

    private Value source(final Location location, final TypeMirror type, final Nullability nullness) {
        graph.apply(new AddValue(scope, location, type, nullness))
    }

    private Port port(final TypeMirror type, final Nullability nullness) {
        Port.reuse('p', type, nullness)
    }

    private SourceLocation access(final String a, final String b) {
        new SourceLocation(new AccessPath([a, b]))
    }

    private ExecutableElement methodNamed(final Class<?> type, final String name) {
        TypeUniverse.of(type).enclosedElements.find {
            it.kind == ElementKind.METHOD && it.simpleName.toString() == name
        } as ExecutableElement
    }
}
