package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.processor.internal.graph.AccessPath
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.AmbientDecl
import io.github.joke.percolate.processor.internal.graph.InputDecl
import io.github.joke.percolate.processor.internal.graph.Location
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.Scope
import io.github.joke.percolate.processor.internal.graph.SourceLocation
import io.github.joke.percolate.processor.internal.graph.Value
import io.github.joke.percolate.processor.nullability.NullabilityResolver
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.ResolveCtx
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * {@link SourceCandidates} unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code type-query-seam}): the sole type question — same-type — is stubbed on a mocked {@code ResolveCtx}, every
 * {@link TypeMirror} is an opaque never-interrogated token, and the graph is a real in-memory structure. No javac.
 * The cases isolate the {@code matchesPort} type/nullness rule, the {@code matchingSource} ranking (pinned source
 * first, then least-id graph source, then a materialised input, else {@code null}), and {@code sourceTypes}.
 */
@Tag('unit')
class SourceCandidatesSpec extends Specification {

    ResolveCtx resolveCtx = Mock()
    NullabilityResolver resolver = Mock()
    Scope scope = Mock()
    MapperGraph graph = new MapperGraph()
    SourceCandidates candidates = new SourceCandidates(graph, new Applier(), resolver, resolveCtx)

    TypeMirror personType = Mock()
    TypeMirror intType = Mock()

    // ---- matchesPort: same type, non-null source satisfies any nullness --------------------------------------

    def 'matchesPort is true for an equal type and compatible nullness'() {
        resolveCtx.isSameType(personType, personType) >> true

        expect:
        candidates.matchesPort(value(personType, source), port(personType, demanded))

        where:
        source               | demanded
        Nullability.NON_NULL | Nullability.NON_NULL
        Nullability.NON_NULL | Nullability.NULLABLE
        Nullability.NULLABLE | Nullability.NULLABLE
    }

    def 'matchesPort is false when a NULLABLE source would feed a NON_NULL port'() {
        resolveCtx.isSameType(personType, personType) >> true

        expect:
        !candidates.matchesPort(value(personType, Nullability.NULLABLE), port(personType, Nullability.NON_NULL))
    }

    def 'matchesPort is false for a different type regardless of nullness'() {
        resolveCtx.isSameType(intType, personType) >> false

        expect:
        !candidates.matchesPort(value(intType, Nullability.NON_NULL), port(personType, Nullability.NON_NULL))
    }

    // ---- matchingSource ranking ------------------------------------------------------------------------------

    def 'matchingSource prefers a matching pinned source over any in-scope candidate'() {
        resolveCtx.isSameType(personType, personType) >> true
        def pinned = value(personType, Nullability.NON_NULL)

        expect:
        candidates.matchingSource(scope, port(personType, Nullability.NON_NULL), pinned).is(pinned)
    }

    def 'matchingSource ignores a non-matching pinned source, materialising the matching parameter instead'() {
        resolveCtx.isSameType(intType, personType) >> false
        resolveCtx.isSameType(personType, personType) >> true
        def pinned = value(intType, Nullability.NON_NULL)
        scope.inputDecls(_) >> Stream.of(inputDecl(personType, Nullability.NON_NULL, leaf('p')))
        def chosen = candidates.matchingSource(scope, port(personType, Nullability.NON_NULL), pinned)

        expect:
        !chosen.is(pinned)
        chosen.loc.role() == Location.Role.LEAF
        chosen.type.get().is(personType)
    }

    def 'matchingSource returns the least-id in-scope graph source when several match'() {
        resolveCtx.isSameType(personType, personType) >> true
        def first = source(access('a', 'b'), personType, Nullability.NON_NULL)
        def second = source(access('c', 'd'), personType, Nullability.NON_NULL)

        expect:
        candidates.matchingSource(scope, port(personType, Nullability.NON_NULL), null) == [first, second].min { it.id() }
    }

    def 'matchingSource materialises the matching parameter when no graph source exists'() {
        resolveCtx.isSameType(personType, personType) >> true
        scope.inputDecls(_) >> Stream.of(inputDecl(personType, Nullability.NON_NULL, leaf('p')))
        def chosen = candidates.matchingSource(scope, port(personType, Nullability.NON_NULL), null)

        expect:
        chosen.loc instanceof SourceLocation
        chosen.loc.role() == Location.Role.LEAF
        chosen.type.get().is(personType)
    }

    def 'matchingSource returns null when nothing in scope matches the port'() {
        scope.inputDecls(_) >> Stream.empty()

        expect:
        candidates.matchingSource(scope, port(personType, Nullability.NON_NULL), null) == null
    }

    def 'matchingSource selects the earlier-declared input when two same-typed inputs both match'() {
        resolveCtx.isSameType(personType, personType) >> true
        def before = inputDecl(personType, Nullability.NON_NULL, leaf('before'))
        def after = inputDecl(personType, Nullability.NON_NULL, leaf('after'))
        scope.inputDecls(_) >> Stream.of(before, after)
        def chosen = candidates.matchingSource(scope, port(personType, Nullability.NON_NULL), null)

        expect:
        chosen.loc == before.location
    }

    // ---- sourceTypes -----------------------------------------------------------------------------------------

    def 'sourceTypes lists the declared parameter input types plus discovered graph sources'() {
        scope.inputDecls(_) >> Stream.of(inputDecl(personType, Nullability.NON_NULL, leaf('p')))
        source(access('x', 'y'), intType, Nullability.NON_NULL)
        def types = candidates.sourceTypes(scope)

        expect:
        types.any { it.is(personType) }
        types.any { it.is(intType) }
    }

    def 'sourceTypes orders declared inputs before discovered graph sources, each in a stable order'() {
        def firstDecl = inputDecl(personType, Nullability.NON_NULL, leaf('first'))
        def secondDecl = inputDecl(intType, Nullability.NON_NULL, leaf('second'))
        scope.inputDecls(_) >> Stream.of(firstDecl, secondDecl)
        def firstGraphSource = source(access('g', 'a'), personType, Nullability.NON_NULL)
        def secondGraphSource = source(access('g', 'b'), intType, Nullability.NON_NULL)

        expect:
        candidates.sourceTypes(scope) == [personType, intType, firstGraphSource.type(), secondGraphSource.type()]
    }

    // ---- byNameSource ------------------------------------------------------------------------------------------

    def 'byNameSource materialises the binding whose key matches the port, when the type is assignable'() {
        resolveCtx.isAssignable(personType, personType) >> true
        scope.ambientDecls(_) >> Stream.of(ambientDecl('order', personType, Nullability.NON_NULL, leaf('order')))
        def bound = candidates.byNameSource(scope, ambientPort('order', personType))

        expect:
        bound.loc == leaf('order')
        bound.type.get().is(personType)
    }

    def 'byNameSource returns null when no binding matches the key'() {
        scope.ambientDecls(_) >> Stream.of(ambientDecl('other', personType, Nullability.NON_NULL, leaf('other')))

        expect:
        candidates.byNameSource(scope, ambientPort('order', personType)) == null
    }

    def "byNameSource returns null when the binding's type is not assignable to the port's declared type"() {
        resolveCtx.isAssignable(intType, personType) >> false
        scope.ambientDecls(_) >> Stream.of(ambientDecl('order', intType, Nullability.NON_NULL, leaf('order')))

        expect:
        candidates.byNameSource(scope, ambientPort('order', personType)) == null
    }

    def 'byNameSource selects the earlier-declared binding when two entries share a key'() {
        resolveCtx.isAssignable(personType, personType) >> true
        def before = ambientDecl('order', personType, Nullability.NON_NULL, leaf('before'))
        def after = ambientDecl('order', personType, Nullability.NON_NULL, leaf('after'))
        scope.ambientDecls(_) >> Stream.of(before, after)
        def bound = candidates.byNameSource(scope, ambientPort('order', personType))

        expect:
        bound.loc == leaf('before')
    }

    // ---- byNameDeclaredType --------------------------------------------------------------------------------------

    def 'byNameDeclaredType reports the declared type of a matching binding regardless of assignability'() {
        scope.ambientDecls(_) >> Stream.of(ambientDecl('order', intType, Nullability.NON_NULL, leaf('order')))

        expect:
        candidates.byNameDeclaredType(scope, ambientPort('order', personType)).get().is(intType)
    }

    def 'byNameDeclaredType is empty when no binding matches the key'() {
        scope.ambientDecls(_) >> Stream.of(ambientDecl('other', personType, Nullability.NON_NULL, leaf('other')))

        expect:
        candidates.byNameDeclaredType(scope, ambientPort('order', personType)).empty
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private Value value(final TypeMirror type, final Nullability nullness) {
        graph.apply(new AddValue(scope, access('leaf', 'one'), type, nullness))
    }

    private Value source(final Location location, final TypeMirror type, final Nullability nullness) {
        graph.apply(new AddValue(scope, location, type, nullness))
    }

    private Port port(final TypeMirror type, final Nullability nullness) {
        Port.byTypeOrDecline('p', type, nullness)
    }

    private SourceLocation access(final String a, final String b) {
        new SourceLocation(new AccessPath([a, b]))
    }

    private SourceLocation leaf(final String segment) {
        new SourceLocation(new AccessPath([segment]))
    }

    private InputDecl inputDecl(final TypeMirror type, final Nullability nullness, final Location location) {
        def decl = Mock(InputDecl)
        decl.type >> type
        decl.nullness >> nullness
        decl.location >> location
        decl
    }

    private AmbientDecl ambientDecl(
            final String key, final TypeMirror type, final Nullability nullness, final Location location) {
        def decl = Mock(AmbientDecl)
        decl.key >> key
        decl.type >> type
        decl.nullness >> nullness
        decl.location >> location
        decl
    }

    private Port ambientPort(final String key, final TypeMirror type) {
        Port.byName(key, type, Nullability.NON_NULL, key)
    }
}
