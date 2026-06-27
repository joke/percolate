package io.github.joke.percolate.processor.internal.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.ChildScopeSpec
import io.github.joke.percolate.spi.Containers
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationSpec
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.PortType
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.ScopeCodegen
import io.github.joke.percolate.spi.SourceProjection
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.HarnessResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * Grounding-by-match (design D2/D5): the engine sources a type-variable port by <b>matching</b> it against an
 * in-scope concrete source, grounding the variable, substituting it across the spec's output and child scope, and
 * instantiating one concrete Operation per match. The work-list never sees an abstract type — every spec the driver
 * lands is concrete. The mechanic is purely structural and names no container kind (it grounds {@code Set} and
 * {@code Stream} identically); a wildcard source argument does not unify (restrict-v1 policy).
 */
@Tag('unit')
class GroundingSpec extends Specification {

    private static final ScopeCodegen MAP =
            { operand, var, body -> CodeBlock.of('$L.map($N -> $L)', operand, var, body) } as ScopeCodegen

    @Shared ResolveCtx ctx = HarnessResolveCtx.create()
    @Shared Grounding grounding = new Grounding(ctx, [])

    @Shared TypeElement setElement = TypeUniverse.elements().getTypeElement('java.util.Set')
    @Shared TypeElement streamElement = TypeUniverse.elements().getTypeElement('java.util.stream.Stream')

    @Shared TypeMirror setOfString = decl('java.util.Set', TypeUniverse.STRING)
    @Shared TypeMirror setOfInteger = decl('java.util.Set', TypeUniverse.INTEGER)
    @Shared TypeMirror setOfLong = decl('java.util.Set', TypeUniverse.LONG_TYPE)
    @Shared TypeMirror streamOfString = decl('java.util.stream.Stream', TypeUniverse.STRING)
    @Shared TypeMirror streamOfInteger = decl('java.util.stream.Stream', TypeUniverse.INTEGER)

    // ---- single match: ground A, substitute across output + child scope --------------------------------------

    def 'a Set<A> port grounds A from a Set<String> source and lands a concrete Set<String> -> Set<Integer>'() {
        when:
        def grounded = grounding.ground(lift(setElement, setOfInteger, TypeUniverse.INTEGER), [setOfString]).toList()

        then: 'exactly one concrete instantiation'
        grounded.size() == 1
        def spec = grounded[0]

        and: 'the port is now concrete Set<String> — no template, no abstract type'
        spec.ports.size() == 1
        spec.ports[0].template == null
        ctx.types().isSameType(spec.ports[0].type, setOfString)

        and: 'the child scope is grounded Person -> PersonView analog: String -> Integer'
        spec.childScope.present
        def child = spec.childScope.get()
        child.elementInTemplate == null
        ctx.types().isSameType(child.elementIn, TypeUniverse.STRING)
        ctx.types().isSameType(child.elementOut, TypeUniverse.INTEGER)

        and: 'output and metadata are preserved'
        ctx.types().isSameType(spec.outputType, setOfInteger)
        spec.label == 'map'
        spec.weight == Weights.CONTAINER
        spec.codegen instanceof ScopeCodegen
    }

    def 'no grounded spec carries any template — the work-list only ever holds concrete Values'() {
        when:
        def grounded = grounding.ground(lift(setElement, setOfInteger, TypeUniverse.INTEGER), [setOfString]).toList()

        then:
        grounded.every { spec ->
            spec.ports.every { it.template == null } &&
                    spec.childScope.get().elementInTemplate == null &&
                    spec.childScope.get().elementOutTemplate == null
        }
    }

    // ---- multiple matching sources over-emit; the engine applies no preference -------------------------------

    def 'two unifying sources instantiate one map each — over-emit, no engine-side choice'() {
        when:
        def grounded = grounding.ground(lift(setElement, setOfInteger, TypeUniverse.INTEGER), [setOfString, setOfLong])
                .toList()

        then: 'one concrete map per source'
        grounded.size() == 2
        def portTypes = grounded.collect { it.ports[0].type }
        portTypes.any { ctx.types().isSameType(it, setOfString) }
        portTypes.any { ctx.types().isSameType(it, setOfLong) }

        and: 'identical weight — the engine prefers neither (cost extraction prunes the unreachable one later)'
        grounded*.weight.toUnique() == [Weights.CONTAINER]
    }

    def 'a source that does not unify contributes nothing — no bridge is invented'() {
        expect: 'a List source cannot feed a Set<A> port'
        grounding.ground(lift(setElement, setOfInteger, TypeUniverse.INTEGER), [TypeUniverse.LIST_OF_STRING]).toList()
                .empty
    }

    // ---- agnostic of container kind: Stream grounds identically to Set ---------------------------------------

    def 'the same mechanic grounds a Stream<A> port — it names no container kind'() {
        when:
        def grounded = grounding.ground(lift(streamElement, streamOfInteger, TypeUniverse.INTEGER), [streamOfString])
                .toList()

        then:
        grounded.size() == 1
        ctx.types().isSameType(grounded[0].ports[0].type, streamOfString)
        ctx.types().isSameType(grounded[0].childScope.get().elementIn, TypeUniverse.STRING)
    }

    // ---- wildcard/bounded-generic policy: restrict in v1 -----------------------------------------------------

    def 'a wildcard source argument does not unify (restrict-v1 policy)'() {
        given:
        def wildcard = ctx.types().getWildcardType(TypeUniverse.STRING, null)
        def setOfWildcard = ctx.types().getDeclaredType(setElement, wildcard)

        expect:
        grounding.ground(lift(setElement, setOfInteger, TypeUniverse.INTEGER), [setOfWildcard]).toList().empty
    }

    // ---- termination: nested generics bound, grounding deterministic / round-trip-safe -----------------------

    def 'nested generics ground at depth and terminate'() {
        given: 'a Set<Set<A>> port matched against a Set<Set<String>> source'
        def setOfSetOfString = ctx.types().getDeclaredType(setElement, setOfString)
        def nestedTemplate = PortType.app(setElement, [PortType.app(setElement, [PortType.variable(0)])])
        def port = new Port('src', setElement.asType(), Nullability.NON_NULL, nestedTemplate)
        def child = ChildScopeSpec.lifted(PortType.variable(0), Nullability.NON_NULL, TypeUniverse.INTEGER,
                Nullability.NON_NULL)
        def spec = OperationSpec.mapping('map', MAP, Weights.CONTAINER, [port], setOfInteger, Nullability.NON_NULL,
                child)

        when:
        def grounded = grounding.ground(spec, [setOfSetOfString]).toList()

        then: 'A grounds to String through two levels of nesting'
        grounded.size() == 1
        ctx.types().isSameType(grounded[0].childScope.get().elementIn, TypeUniverse.STRING)
        ctx.types().isSameType(grounded[0].ports[0].type, setOfSetOfString)
    }

    def 'grounding the same spec twice is deterministic (round-trip-safe, no divergence)'() {
        given:
        def spec = lift(setElement, setOfInteger, TypeUniverse.INTEGER)

        when:
        def first = grounding.ground(spec, [setOfString]).toList()
        def second = grounding.ground(spec, [setOfString]).toList()

        then:
        first.size() == 1
        second.size() == 1
        ctx.types().isSameType(first[0].ports[0].type, second[0].ports[0].type)
        ctx.types().isSameType(first[0].childScope.get().elementIn, second[0].childScope.get().elementIn)
    }

    // ---- SourceProjection widening: cross-kind bootstrap (D8) -------------------------------------------------

    def 'a SourceProjection widens the match set so a Stream<A> port grounds from a List<String> source'() {
        given: 'a projector that views any List<X> as Stream<X> (the collection->stream bridge)'
        def listToStream = { TypeMirror source, ResolveCtx c ->
            Containers.isList(source, c)
                    ? Stream.of(c.types().getDeclaredType(streamElement, Containers.typeArgument(source, 0)))
                    : Stream.<TypeMirror> empty()
        } as SourceProjection
        def widening = new Grounding(ctx, [listToStream])

        when: 'grounding a Stream<A> map against only a List<String> source (no direct Stream source)'
        def grounded = widening.ground(lift(streamElement, streamOfInteger, TypeUniverse.INTEGER),
                [TypeUniverse.LIST_OF_STRING]).toList()

        then: 'A grounds to String via the projected Stream<String>; the work-list stays concrete'
        grounded.size() == 1
        ctx.types().isSameType(grounded[0].ports[0].type, streamOfString)
        ctx.types().isSameType(grounded[0].childScope.get().elementIn, TypeUniverse.STRING)
        grounded[0].ports[0].template == null
    }

    def 'with no projections registered, grounding falls back to the raw source set (additive)'() {
        expect: 'a List<String> source alone cannot feed a Stream<A> port'
        new Grounding(ctx, []).ground(lift(streamElement, streamOfInteger, TypeUniverse.INTEGER),
                [TypeUniverse.LIST_OF_STRING]).toList().empty
    }

    // ---- concrete specs are untouched (additive) -------------------------------------------------------------

    def 'a spec with no type-variable port passes through grounding unchanged'() {
        given:
        def concrete = OperationSpec.of('iterate',
                { inputs -> CodeBlock.of('$L.stream()', inputs.single()) } as io.github.joke.percolate.spi.OperationCodegen,
                Weights.CONTAINER, [new Port('src', setOfString, Nullability.NON_NULL)], streamOfString,
                Nullability.NON_NULL)

        when:
        def grounded = grounding.ground(concrete, [setOfString, setOfLong]).toList()

        then: 'returned as-is, regardless of how many sources are in scope'
        grounded.size() == 1
        grounded[0].is(concrete)
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    /** A functor-lift {@code F<B> ← F<A>} over {@code erasure}: port {@code App(F,[Var 0])}, child {@code A → B}. */
    private static OperationSpec lift(final TypeElement erasure, final TypeMirror output, final TypeMirror elementOut) {
        def template = PortType.app(erasure, [PortType.variable(0)])
        def port = new Port('src', erasure.asType(), Nullability.NON_NULL, template)
        def child = ChildScopeSpec.lifted(PortType.variable(0), Nullability.NON_NULL, elementOut, Nullability.NON_NULL)
        OperationSpec.mapping('map', MAP, Weights.CONTAINER, [port], output, Nullability.NON_NULL, child)
    }

    private static TypeMirror decl(final String fqn, final TypeMirror arg) {
        TypeUniverse.types().getDeclaredType(TypeUniverse.elements().getTypeElement(fqn), arg)
    }
}
