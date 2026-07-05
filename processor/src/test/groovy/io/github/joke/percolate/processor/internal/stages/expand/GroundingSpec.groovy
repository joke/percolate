package io.github.joke.percolate.processor.internal.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.test.FakeResolveCtx
import io.github.joke.percolate.processor.test.FakeType
import io.github.joke.percolate.spi.ChildScopeSpec
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.OperationSpec
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.PortType
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.ScopeCodegen
import io.github.joke.percolate.spi.SourceProjection
import io.github.joke.percolate.spi.Weights
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * Grounding-by-match (design D2/D5): the engine sources a type-variable port by <b>matching</b> it against an
 * in-scope concrete source, grounding the variable, substituting it across the spec's output and child scope, and
 * instantiating one concrete Operation per match. The work-list never sees an abstract type — every spec the driver
 * lands is concrete. The mechanic is purely structural and names no container kind (it grounds {@code Set} and
 * {@code Stream} identically); a wildcard source argument does not unify (restrict-v1 policy).
 *
 * <p>Unit-tested mock-only over the {@link ResolveCtx} type-query seam (change {@code type-query-seam}): a
 * {@link FakeResolveCtx} answers every seam question structurally over {@link FakeType} — no javac, no shared
 * static substrate, parallel-safe by construction (no {@code @Isolated}).
 */
@Tag('unit')
class GroundingSpec extends Specification {

    private static final ScopeCodegen MAP =
            { operand, var, body -> CodeBlock.of('$L.map($N -> $L)', operand, var, body) } as ScopeCodegen
    private static final OperationCodegen OP = { inputs -> CodeBlock.of('x') } as OperationCodegen

    @Shared ResolveCtx ctx = new FakeResolveCtx()
    @Shared Grounding grounding = new Grounding(ctx, [])

    @Shared TypeElement setElement = Stub(TypeElement)
    @Shared TypeElement streamElement = Stub(TypeElement)
    @Shared TypeElement listElement = Stub(TypeElement)
    @Shared TypeElement stringElement = Stub(TypeElement)
    @Shared TypeElement integerElement = Stub(TypeElement)
    @Shared TypeElement longElement = Stub(TypeElement)

    @Shared TypeMirror STRING = FakeType.declared(stringElement)
    @Shared TypeMirror INTEGER = FakeType.declared(integerElement)
    @Shared TypeMirror LONG = FakeType.declared(longElement)

    @Shared TypeMirror setOfString = FakeType.declared(setElement, STRING)
    @Shared TypeMirror setOfInteger = FakeType.declared(setElement, INTEGER)
    @Shared TypeMirror setOfLong = FakeType.declared(setElement, LONG)
    @Shared TypeMirror streamOfString = FakeType.declared(streamElement, STRING)
    @Shared TypeMirror streamOfInteger = FakeType.declared(streamElement, INTEGER)
    @Shared TypeMirror listOfString = FakeType.declared(listElement, STRING)

    def setupSpec() {
        setElement.asType() >> FakeType.declared(setElement)
        streamElement.asType() >> FakeType.declared(streamElement)
    }

    // ---- single match: ground A, substitute across output + child scope --------------------------------------

    def 'a Set<A> port grounds A from a Set<String> source and lands a concrete Set<String> -> Set<Integer>'() {
        when:
        def grounded = grounding.ground(lift(setElement, setOfInteger, INTEGER), [setOfString]).toList()

        then: 'exactly one concrete instantiation'
        grounded.size() == 1
        def spec = grounded[0]

        and: 'the port is now concrete Set<String> — no template, no abstract type'
        spec.ports.size() == 1
        spec.ports[0].template == null
        ctx.isSameType(spec.ports[0].type, setOfString)

        and: 'the child scope is grounded Person -> PersonView analog: String -> Integer'
        spec.childScope.present
        def child = spec.childScope.get()
        child.elementInTemplate == null
        ctx.isSameType(child.elementIn, STRING)
        ctx.isSameType(child.elementOut, INTEGER)

        and: 'output and metadata are preserved'
        ctx.isSameType(spec.outputType, setOfInteger)
        spec.label == 'map'
        spec.weight == Weights.CONTAINER
        spec.codegen instanceof ScopeCodegen
    }

    def 'no grounded spec carries any template — the work-list only ever holds concrete Values'() {
        when:
        def grounded = grounding.ground(lift(setElement, setOfInteger, INTEGER), [setOfString]).toList()

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
        def grounded = grounding.ground(lift(setElement, setOfInteger, INTEGER), [setOfString, setOfLong])
                .toList()

        then: 'one concrete map per source'
        grounded.size() == 2
        def portTypes = grounded.collect { it.ports[0].type }
        portTypes.any { ctx.isSameType(it, setOfString) }
        portTypes.any { ctx.isSameType(it, setOfLong) }

        and: 'identical weight — the engine prefers neither (cost extraction prunes the unreachable one later)'
        grounded*.weight.toUnique() == [Weights.CONTAINER]
    }

    def 'a source that does not unify contributes nothing — no bridge is invented'() {
        expect: 'a List source cannot feed a Set<A> port'
        grounding.ground(lift(setElement, setOfInteger, INTEGER), [listOfString]).toList().empty
    }

    // ---- agnostic of container kind: Stream grounds identically to Set ---------------------------------------

    def 'the same mechanic grounds a Stream<A> port — it names no container kind'() {
        when:
        def grounded = grounding.ground(lift(streamElement, streamOfInteger, INTEGER), [streamOfString])
                .toList()

        then:
        grounded.size() == 1
        ctx.isSameType(grounded[0].ports[0].type, streamOfString)
        ctx.isSameType(grounded[0].childScope.get().elementIn, STRING)
    }

    // ---- wildcard/bounded-generic policy: restrict in v1 -----------------------------------------------------

    def 'a wildcard source argument does not unify (restrict-v1 policy)'() {
        given:
        def wildcard = FakeType.marker(TypeKind.WILDCARD)
        def setOfWildcard = ctx.declaredType(setElement, wildcard)

        expect:
        grounding.ground(lift(setElement, setOfInteger, INTEGER), [setOfWildcard]).toList().empty
    }

    // ---- termination: nested generics bound, grounding deterministic / round-trip-safe -----------------------

    def 'nested generics ground at depth and terminate'() {
        given: 'a Set<Set<A>> port matched against a Set<Set<String>> source'
        def setOfSetOfString = ctx.declaredType(setElement, setOfString)
        def nestedTemplate = PortType.app(setElement, [PortType.app(setElement, [PortType.variable(0)])])
        def port = new Port('src', setElement.asType(), Nullability.NON_NULL, nestedTemplate)
        def child = ChildScopeSpec.lifted(PortType.variable(0), Nullability.NON_NULL, INTEGER,
                Nullability.NON_NULL)
        def spec = OperationSpec.mapping('map', MAP, Weights.CONTAINER, [port], setOfInteger, Nullability.NON_NULL,
                child)

        when:
        def grounded = grounding.ground(spec, [setOfSetOfString]).toList()

        then: 'A grounds to String through two levels of nesting'
        grounded.size() == 1
        ctx.isSameType(grounded[0].childScope.get().elementIn, STRING)
        ctx.isSameType(grounded[0].ports[0].type, setOfSetOfString)
    }

    def 'grounding the same spec twice is deterministic (round-trip-safe, no divergence)'() {
        given:
        def spec = lift(setElement, setOfInteger, INTEGER)

        when:
        def first = grounding.ground(spec, [setOfString]).toList()
        def second = grounding.ground(spec, [setOfString]).toList()

        then:
        first.size() == 1
        second.size() == 1
        ctx.isSameType(first[0].ports[0].type, second[0].ports[0].type)
        ctx.isSameType(first[0].childScope.get().elementIn, second[0].childScope.get().elementIn)
    }

    // ---- SourceProjection widening: cross-kind bootstrap (D8) -------------------------------------------------

    def 'a SourceProjection widens the match set so a Stream<A> port grounds from a List<String> source'() {
        given: 'a projector that views any List<X> as Stream<X> (the collection->stream bridge)'
        def listToStream = { TypeMirror source, ResolveCtx c ->
            (source instanceof FakeType && source.identity == listElement)
                    ? Stream.of(c.declaredType(streamElement, c.typeArgument(source, 0)))
                    : Stream.<TypeMirror> empty()
        } as SourceProjection
        def widening = new Grounding(ctx, [listToStream])

        when: 'grounding a Stream<A> map against only a List<String> source (no direct Stream source)'
        def grounded = widening.ground(lift(streamElement, streamOfInteger, INTEGER),
                [listOfString]).toList()

        then: 'A grounds to String via the projected Stream<String>; the work-list stays concrete'
        grounded.size() == 1
        ctx.isSameType(grounded[0].ports[0].type, streamOfString)
        ctx.isSameType(grounded[0].childScope.get().elementIn, STRING)
        grounded[0].ports[0].template == null
    }

    def 'with no projections registered, grounding falls back to the raw source set (additive)'() {
        expect: 'a List<String> source alone cannot feed a Stream<A> port'
        new Grounding(ctx, []).ground(lift(streamElement, streamOfInteger, INTEGER),
                [listOfString]).toList().empty
    }

    // ---- concrete specs are untouched (additive) -------------------------------------------------------------

    def 'a spec with no type-variable port passes through grounding unchanged'() {
        given:
        def concrete = OperationSpec.of('iterate',
                { inputs -> CodeBlock.of('$L.stream()', inputs.single()) } as OperationCodegen,
                Weights.CONTAINER, [new Port('src', setOfString, Nullability.NON_NULL)], streamOfString,
                Nullability.NON_NULL)

        when:
        def grounded = grounding.ground(concrete, [setOfString, setOfLong]).toList()

        then: 'returned as-is, regardless of how many sources are in scope'
        grounded.size() == 1
        grounded[0].is(concrete)
    }

    // ---- degenerate + multi-port templates: Concrete, shared variable, passthrough --------------------------

    def 'a Concrete template port grounds by isSameType and lands a concrete variable-free spec'() {
        // a template that carries no variable — a Concrete Set<String>
        def port = new Port('src', setOfString, Nullability.NON_NULL, PortType.concrete(setOfString))
        def spec = OperationSpec.of('copy', OP, Weights.STEP, [port], setOfString, Nullability.NON_NULL)

        when:
        def grounded = grounding.ground(spec, [setOfString]).toList()

        then:
        grounded.size() == 1
        grounded[0].ports[0].template == null
        ctx.isSameType(grounded[0].ports[0].type, setOfString)
        grounded[0].childScope.empty
    }

    def 'a variable shared across two ports must bind consistently — mixed bindings are pruned'() {
        // two Set<A> ports: A is bound on the first, then re-checked for equality on the second
        def port0 = new Port('a', setElement.asType(), Nullability.NON_NULL, PortType.app(setElement, [PortType.variable(0)]))
        def port1 = new Port('b', setElement.asType(), Nullability.NON_NULL, PortType.app(setElement, [PortType.variable(0)]))
        def spec = OperationSpec.of('zip', OP, Weights.STEP, [port0, port1], setOfInteger, Nullability.NON_NULL)

        when: 'Set<String> and Set<Integer> are in scope — only the two matching-pair bindings survive'
        def grounded = grounding.ground(spec, [setOfString, setOfInteger]).toList()

        then:
        grounded.size() == 2
        grounded.every { ctx.isSameType(it.ports[0].type, it.ports[1].type) }
        grounded.every { it.childScope.empty }
    }

    def 'an already-concrete port beside a template port passes through grounding unchanged'() {
        def templatePort = new Port('a', setElement.asType(), Nullability.NON_NULL,
                PortType.app(setElement, [PortType.variable(0)]))
        def concretePort = new Port('b', setOfString, Nullability.NON_NULL)
        def spec = OperationSpec.of('merge', OP, Weights.STEP, [templatePort, concretePort], setOfInteger,
                Nullability.NON_NULL)

        when:
        def grounded = grounding.ground(spec, [setOfString]).toList()

        then:
        grounded.size() == 1
        grounded[0].ports[1].template == null
        ctx.isSameType(grounded[0].ports[1].type, setOfString)
    }

    def 'a partial template spec instantiates through the partial path'() {
        def port = new Port('src', setElement.asType(), Nullability.NON_NULL,
                PortType.app(setElement, [PortType.variable(0)]))
        def spec = OperationSpec.ofPartial('firstOrThrow', OP, Weights.STEP, [port], INTEGER,
                Nullability.NON_NULL)

        when:
        def grounded = grounding.ground(spec, [setOfString]).toList()

        then:
        grounded.size() == 1
        grounded[0].partial
        grounded[0].childScope.empty
    }

    // ---- unification edges: non-declared source, arity mismatch, array binding, ungrounded var --------------

    def 'an App template does not unify against a non-declared (array) source'() {
        def arrayOfString = ctx.arrayType(STRING)

        expect: 'a String[] source is not a DECLARED type, so a Set<A> port cannot unify with it'
        grounding.ground(lift(setElement, setOfInteger, INTEGER), [arrayOfString]).toList().empty
    }

    def 'an App template does not unify against a raw source of mismatched arity'() {
        def rawSet = ctx.erasure(setElement.asType())

        expect: 'raw Set has zero type arguments; a Set<A> template expects one, so the arity differs'
        grounding.ground(lift(setElement, setOfInteger, INTEGER), [rawSet]).toList().empty
    }

    def 'a type variable grounds to an array argument — an invariant reference type'() {
        def arrayOfString = ctx.arrayType(STRING)
        def setOfStringArray = ctx.declaredType(setElement, arrayOfString)

        when:
        def grounded = grounding.ground(lift(setElement, setOfInteger, INTEGER), [setOfStringArray]).toList()

        then:
        grounded.size() == 1
        ctx.isSameType(grounded[0].ports[0].type, setOfStringArray)
        ctx.isSameType(grounded[0].childScope.get().elementIn, arrayOfString)
    }

    def 'unification refuses a template nested past the recursion bound'() {
        // a Set<Set<...<A>>> template and a matching Set<Set<...<String>>> source, both nested past MAX_DEPTH
        def template = PortType.variable(0)
        def sourceType = STRING
        40.times {
            template = PortType.app(setElement, [template])
            sourceType = ctx.declaredType(setElement, sourceType)
        }
        def port = new Port('deep', setElement.asType(), Nullability.NON_NULL, template)
        def spec = OperationSpec.of('deep', OP, Weights.STEP, [port], setOfString, Nullability.NON_NULL)

        expect: 'nesting beyond the bound aborts unification, so nothing grounds'
        grounding.ground(spec, [sourceType]).toList().empty
    }

    def 'an ungrounded type variable in the child scope fails fast during instantiation'() {
        // the child scope references Var 1, but only Var 0 is ever bound by a port
        def port = new Port('src', setElement.asType(), Nullability.NON_NULL,
                PortType.app(setElement, [PortType.variable(0)]))
        def child = ChildScopeSpec.lifted(PortType.variable(1), Nullability.NON_NULL, INTEGER,
                Nullability.NON_NULL)
        def spec = OperationSpec.mapping('map', MAP, Weights.CONTAINER, [port], setOfInteger, Nullability.NON_NULL, child)

        when:
        grounding.ground(spec, [setOfString]).toList()

        then:
        def error = thrown(IllegalStateException)

        expect:
        error.message.contains('Ungrounded type variable')
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    /** A functor-lift {@code F<B> ← F<A>} over {@code erasure}: port {@code App(F,[Var 0])}, child {@code A → B}. */
    private static OperationSpec lift(final TypeElement erasure, final TypeMirror output, final TypeMirror elementOut) {
        def template = PortType.app(erasure, [PortType.variable(0)])
        def port = new Port('src', erasure.asType(), Nullability.NON_NULL, template)
        def child = ChildScopeSpec.lifted(PortType.variable(0), Nullability.NON_NULL, elementOut, Nullability.NON_NULL)
        OperationSpec.mapping('map', MAP, Weights.CONTAINER, [port], output, Nullability.NON_NULL, child)
    }
}
