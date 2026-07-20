package io.github.joke.percolate.spi

import io.github.joke.percolate.javapoet.CodeBlock
import io.github.joke.percolate.spi.test.FakeResolveCtx
import io.github.joke.percolate.spi.test.FakeType
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * Exercises the single {@link Container} base through a sequence-shaped and a wrapper-shaped test subclass. The base is
 * fully <b>target-driven</b> (design D1/D8): every operation is keyed on the demanded target alone — no candidate is
 * read. Container kind is <b>emergent from which operations are supplied</b>: a sequence supplies {@code collect}; a
 * presence wrapper does not (supplying {@code wrap}/{@code unwrap}/{@code mapPresence} instead). The per-element
 * transform over the intermediate lives in the generic stream strategy, so no plain operation here carries a child
 * scope — only a wrapper's same-kind {@code mapPresence}, emitted as a type-variable functor lift.
 *
 * <p>Unit-tested mock-only over the {@link ResolveCtx} type-query seam (change {@code type-query-seam}): a
 * {@link FakeResolveCtx} answers every seam question structurally over {@link FakeType} — no javac, no shared
 * static substrate, parallel-safe by construction (no {@code @Isolated}). The private assembly helpers
 * ({@link Container#produceMyKind}/{@link Container#iterateInto}/{@link Container#unwrapInto}/
 * {@link Container#isIntermediate}/{@link Container#intermediateElement}/{@link Container#intermediateOf}/
 * {@link Container#unary}) are widened to {@code protected} test seams (change {@code clean-up-test-coverage-tooling})
 * and exercised directly, alongside the public {@link Container#expand}/{@link Container#project} entry points.
 */
@Tag('unit')
class ContainerSpec extends Specification {

    @Shared TypeElement listElement = Stub(TypeElement)
    @Shared TypeElement optionalElement = Stub(TypeElement)
    @Shared TypeElement streamElement = Stub(TypeElement)
    @Shared TypeElement setElement = Stub(TypeElement)
    @Shared TypeElement stringElement = Stub(TypeElement)
    @Shared TypeElement integerElement = Stub(TypeElement)

    @Shared ResolveCtx ctx = new FakeResolveCtx()

    @Shared TypeMirror STRING = FakeType.declared(stringElement)
    @Shared TypeMirror INTEGER = FakeType.declared(integerElement)
    @Shared TypeMirror listOfString = FakeType.declared(listElement, STRING)
    @Shared TypeMirror listOfInteger = FakeType.declared(listElement, INTEGER)
    @Shared TypeMirror setOfString = FakeType.declared(setElement, STRING)
    @Shared TypeMirror optionalOfString = FakeType.declared(optionalElement, STRING)
    @Shared TypeMirror streamOfString = FakeType.declared(streamElement, STRING)

    def setupSpec() {
        listElement.asType() >> FakeType.declared(listElement)
        optionalElement.asType() >> FakeType.declared(optionalElement)
        streamElement.asType() >> FakeType.declared(streamElement)
        ctx.named('java.util.List', listElement)
        ctx.named('java.util.Optional', optionalElement)
        ctx.named('java.util.stream.Stream', streamElement)
    }

    // ---- kind is emergent ----------------------------------------------------------------------------------

    def 'a container supplying collect is a sequence; one omitting it is a presence wrapper'() {
        expect:
        new TestSeq().collect().present
        !new TestWrapper().collect().present
        new TestWrapper().mapPresence().present
        !new TestSeq().mapPresence().present
    }

    // ---- sequence (collect present) ------------------------------------------------------------------------

    def 'a sequence iterates into its Stream<E> intermediate, plain, no child scope'() {
        when:
        def specs = new TestSeq().expand(demand(streamOfString), ctx).toList()

        then:
        specs.size() == 1
        def iterate = specs[0]
        iterate.childScope.empty
        iterate.codegen instanceof OperationCodegen
        iterate.weight == Weights.CONTAINER
        ctx.isSameType(iterate.ports[0].type, listOfString)
        ctx.isSameType(iterate.outputType, streamOfString)
        !iterate.partial
    }

    def 'a sequence target collects a Stream<E> and wraps a scalar, both plain'() {
        when:
        def specs = new TestSeq().expand(demand(listOfString), ctx).toList()

        then: 'a plain collect Stream<String> -> List<String>'
        def collect = specs.find { ctx.isSameType(it.ports[0].type, streamOfString) }
        collect != null
        collect.childScope.empty
        collect.codegen instanceof OperationCodegen
        ctx.isSameType(collect.outputType, listOfString)

        and: 'a plain single-element wrap String -> List<String>, its port NON_NULL by default (List.of rejects null)'
        def wrap = specs.find { ctx.isSameType(it.ports[0].type, STRING) }
        wrap != null
        wrap.childScope.empty
        ctx.isSameType(wrap.outputType, listOfString)
        wrap.ports[0].nullness == Nullability.NON_NULL

        and: 'nothing carries a child scope (no fused element mapping)'
        specs.every { it.childScope.empty }
    }

    def 'a same-kind List<B> target never emits an iterate (reshaping is left to the stream chain)'() {
        when:
        def specs = new TestSeq().expand(demand(listOfInteger), ctx).toList()

        then:
        specs.every { !Containers.isStream(it.outputType, ctx) }
        specs.every { it.childScope.empty }
    }

    def 'a sequence with no single-element factory collects but does not wrap'() {
        when:
        def specs = new TestSeq(wrappable: false).expand(demand(listOfString), ctx).toList()

        then:
        specs.size() == 1
        ctx.isSameType(specs[0].ports[0].type, streamOfString)
        ctx.isSameType(specs[0].outputType, listOfString)
    }

    def 'a sequence declines a target that is neither its kind nor its intermediate'() {
        expect:
        new TestSeq().expand(demand(setOfString), ctx).toList().empty
    }

    // ---- presence wrapper (collect absent) ----------------------------------------------------------------

    def 'a wrapper wraps a scalar and maps presence in its own kind, as a functor lift'() {
        when:
        def specs = new TestWrapper().expand(demand(optionalOfString), ctx).toList()

        then: 'a plain wrap String -> Optional<String>, no child scope, port NON_NULL by default'
        def wrap = specs.find { it.childScope.empty && ctx.isSameType(it.ports[0].type, STRING) }
        wrap != null
        wrap.codegen instanceof OperationCodegen
        ctx.isSameType(wrap.outputType, optionalOfString)
        wrap.ports[0].nullness == Nullability.NON_NULL

        and: 'a scope-owning functor-lift mapPresence Optional<A> -> Optional<String> over a type-variable port'
        def mapping = specs.find { it.childScope.present }
        mapping != null
        mapping.codegen instanceof ScopeCodegen
        mapping.ports[0].template == PortType.app(optionalElement, [PortType.variable(0)])
        def child = mapping.childScope.get()
        child.elementInTemplate == PortType.variable(0)
        ctx.isSameType(child.elementOut, STRING)
        ctx.isSameType(mapping.outputType, optionalOfString)
        !mapping.partial
    }

    def 'a wrapper iterates a 0-or-1 Stream<E> from its own kind and supplies no collect'() {
        when:
        def specs = new TestWrapper().expand(demand(streamOfString), ctx).toList()

        then: 'the iterate produces Stream<String> from Optional<String> (the wrapper of the stream element)'
        def iterate = specs.find { ctx.isSameType(it.ports[0].type, optionalOfString) }
        iterate != null
        iterate.childScope.empty
        iterate.codegen instanceof OperationCodegen
        ctx.isSameType(iterate.outputType, streamOfString)
        !iterate.partial
        specs.every { !it.childScope.present }
    }

    def 'a wrapper may declare its wrap element port nullable, e.g. Optional.ofNullable'() {
        when:
        def specs = new TestWrapper(wrapNullable: true).expand(demand(optionalOfString), ctx).toList()

        then:
        def wrap = specs.find { it.childScope.empty && ctx.isSameType(it.ports[0].type, STRING) }
        wrap != null
        wrap.ports[0].nullness == Nullability.NULLABLE
    }

    def 'a wrapper unwraps a scalar target, plain and partial, under the demanded nullness'() {
        when:
        def specs = new TestWrapper().expand(demand(STRING, Nullability.NULLABLE), ctx).toList()

        then:
        specs.size() == 1
        def unwrap = specs[0]
        unwrap.childScope.empty
        unwrap.codegen instanceof OperationCodegen
        unwrap.partial
        ctx.isSameType(unwrap.ports[0].type, optionalOfString)
        ctx.isSameType(unwrap.outputType, STRING)
        unwrap.outputNullness == Nullability.NULLABLE
    }

    // ---- project (source-facing half of element mapping) --------------------------------------------------

    def 'a container that can be opened projects its own kind to its intermediate'() {
        when:
        def projected = new TestSeq().project(listOfString, ctx).toList()

        then:
        projected.size() == 1
        ctx.isSameType(projected[0], streamOfString)
    }

    def 'a container declines projecting a source of a different kind'() {
        expect:
        new TestSeq().project(optionalOfString, ctx).toList().empty
    }

    def 'a container that cannot be opened (no iterate) never projects'() {
        expect:
        new TestBare().project(listOfString, ctx).toList().empty
    }

    // ---- produceMyKind (widened test seam) -----------------------------------------------------------------

    def 'produceMyKind emits collect and wrap for a sequence (no mapPresence supplied)'() {
        def specs = Stream.<OperationSpec> builder()

        when:
        new TestSeq().produceMyKind(listOfString, ctx, specs)
        def result = specs.build().toList()

        then:
        result*.label as Set == ['collect', 'wrap'] as Set
        def collect = result.find { it.label == 'collect' }
        collect.weight == Weights.CONTAINER
        collect.codegen instanceof OperationCodegen
        collect.childScope.empty
        !collect.partial
        ctx.isSameType(collect.ports[0].type, streamOfString)
        collect.ports[0].nullness == Nullability.NON_NULL
        ctx.isSameType(collect.outputType, listOfString)
        collect.outputNullness == Nullability.NON_NULL
        def wrap = result.find { it.label == 'wrap' }
        wrap.weight == Weights.CONTAINER
        ctx.isSameType(wrap.ports[0].type, STRING)
        ctx.isSameType(wrap.outputType, listOfString)
    }

    def 'produceMyKind emits wrap and map for a wrapper (no collect supplied)'() {
        def specs = Stream.<OperationSpec> builder()

        when:
        new TestWrapper().produceMyKind(optionalOfString, ctx, specs)
        def result = specs.build().toList()

        then:
        result*.label as Set == ['wrap', 'map'] as Set
        def map = result.find { it.label == 'map' }
        map.weight == Weights.CONTAINER
        map.codegen instanceof ScopeCodegen
        map.ports[0].name == 'source'
        map.ports[0].template == PortType.app(optionalElement, [PortType.variable(0)])
        map.childScope.present
        def child = map.childScope.get()
        child.elementInTemplate == PortType.variable(0)
        ctx.isSameType(child.elementOut, STRING)
        !map.partial
    }

    def 'produceMyKind omits map when mapPresence is supplied but the kind has no erasure'() {
        def specs = Stream.<OperationSpec> builder()

        when:
        new TestWrapper(hasKindErasure: false).produceMyKind(optionalOfString, ctx, specs)
        def result = specs.build().toList()

        then:
        result.size() == 1
        result[0].label == 'wrap'
    }

    def 'produceMyKind omits collect when the element cannot form the intermediate'() {
        def specs = Stream.<OperationSpec> builder()
        def listOfInt = FakeType.declared(listElement, FakeType.marker(TypeKind.INT))

        when:
        new TestSeq().produceMyKind(listOfInt, ctx, specs)
        def result = specs.build().toList()

        then:
        result.size() == 1
        result[0].label == 'wrap'
    }

    def 'produceMyKind emits nothing when neither collect, wrap, nor mapPresence is supplied'() {
        def specs = Stream.<OperationSpec> builder()

        when:
        new TestBare().produceMyKind(listOfString, ctx, specs)

        then:
        specs.build().toList().empty
    }

    // ---- iterateInto (widened test seam) -------------------------------------------------------------------

    def 'iterateInto emits an iterate op from this kind when the intermediate element forms a source'() {
        def specs = Stream.<OperationSpec> builder()

        when:
        new TestSeq().iterateInto(streamOfString, ctx, specs)
        def result = specs.build().toList()

        then:
        result.size() == 1
        result[0].label == 'iterate'
        result[0].codegen instanceof OperationCodegen
        result[0].childScope.empty
        !result[0].partial
        ctx.isSameType(result[0].ports[0].type, listOfString)
        result[0].ports[0].nullness == Nullability.NON_NULL
        ctx.isSameType(result[0].outputType, streamOfString)
    }

    def 'iterateInto emits nothing when iterate is not supplied'() {
        def specs = Stream.<OperationSpec> builder()

        when:
        new TestBare().iterateInto(streamOfString, ctx, specs)

        then:
        specs.build().toList().empty
    }

    def 'iterateInto emits nothing when the intermediate element cannot form this kind'() {
        def specs = Stream.<OperationSpec> builder()
        def streamOfInt = FakeType.declared(streamElement, FakeType.marker(TypeKind.INT))

        when:
        new TestSeq().iterateInto(streamOfInt, ctx, specs)

        then:
        specs.build().toList().empty
    }

    // ---- unwrapInto (widened test seam) --------------------------------------------------------------------

    def 'unwrapInto emits a partial unwrap op reusing an in-scope wrapper source'() {
        def specs = Stream.<OperationSpec> builder()

        when:
        new TestWrapper().unwrapInto(STRING, demand(STRING, Nullability.NULLABLE), ctx, specs)
        def result = specs.build().toList()

        then:
        result.size() == 1
        result[0].label == 'unwrap'
        result[0].partial
        result[0].childScope.empty
        ctx.isSameType(result[0].ports[0].type, optionalOfString)
        result[0].ports[0].sourcing == Port.Sourcing.REUSE
        ctx.isSameType(result[0].outputType, STRING)
        result[0].outputNullness == Nullability.NULLABLE
    }

    def 'unwrapInto emits nothing when unwrap is not supplied'() {
        def specs = Stream.<OperationSpec> builder()

        when:
        new TestSeq().unwrapInto(STRING, demand(STRING), ctx, specs)

        then:
        specs.build().toList().empty
    }

    def 'unwrapInto emits nothing when the target cannot form this kind of wrapper'() {
        def specs = Stream.<OperationSpec> builder()
        def intType = FakeType.marker(TypeKind.INT)

        when:
        new TestWrapper().unwrapInto(intType, demand(intType), ctx, specs)

        then:
        specs.build().toList().empty
    }

    // ---- isIntermediate / intermediateElement / intermediateOf / unary (widened test seams) -----------------

    def 'isIntermediate is true only for a declared type erasure-equal to the intermediateErasure'() {
        expect:
        new TestSeq().isIntermediate(streamOfString, ctx)
        !new TestSeq().isIntermediate(listOfString, ctx)
        !new TestSeq().isIntermediate(FakeType.marker(TypeKind.INT), ctx)
    }

    def 'intermediateElement reads the first type argument of the intermediate'() {
        expect:
        ctx.isSameType(Container.intermediateElement(streamOfString, ctx), STRING)
    }

    def 'intermediateOf forms the intermediate erasure over a reference element'() {
        expect:
        ctx.isSameType(new TestSeq().intermediateOf(STRING, ctx).get(), streamOfString)
    }

    def 'intermediateOf is empty for a non-reference element'() {
        expect:
        !new TestSeq().intermediateOf(FakeType.marker(TypeKind.INT), ctx).present
    }

    def 'unary renders the snippet over the single incoming value'() {
        def operand = CodeBlock.of('$L', 'operand')
        def rendered = CodeBlock.of('$L', 'rendered')
        def wrong = CodeBlock.of('$L', 'wrong')
        Container.UnarySnippet snippet = { CodeBlock value -> value == operand ? rendered : wrong } as Container.UnarySnippet
        IncomingValues inputs = Mock()

        when:
        def result = Container.unary(snippet).render(inputs)

        then:
        1 * inputs.single() >> operand
        0 * _

        expect:
        result.toString() == rendered.toString()
    }

    // ---- containerOf (already-protected test seam) ----------------------------------------------------------

    def 'containerOf builds this kind over a reference element when a kindErasure exists'() {
        expect:
        ctx.isSameType(new TestSeq().containerOf(STRING, ctx).get(), listOfString)
    }

    def 'containerOf is empty for a non-reference element'() {
        expect:
        !new TestSeq().containerOf(FakeType.marker(TypeKind.INT), ctx).present
    }

    def 'containerOf is empty when the kind has no erasure'() {
        expect:
        !new TestSeq(hasKindErasure: false).containerOf(STRING, ctx).present
    }

    private static ProduceDemand demand(final TypeMirror target, final Nullability nullness = Nullability.NON_NULL) {
        [
                targetType      : { target },
                targetNullness  : { nullness },
                directive       : { Optional.empty() },
                declaredChildren: { [] as Set },
                bindingName     : { '' },
                nullnessOf      : { TypeMirror t, Element s -> Nullability.NON_NULL },
        ] as ProduceDemand
    }

    /** A List-shaped sequence: matches List, element is its first type argument, optionally has a List.of wrap. */
    static class TestSeq extends Container {

        boolean wrappable = true
        boolean hasKindErasure = true

        @Override
        Optional<UnarySnippet> iterate() {
            Optional.of({ container -> CodeBlock.of('$L.stream()', container) } as UnarySnippet)
        }

        @Override
        Optional<UnarySnippet> collect() {
            Optional.of({ stream -> CodeBlock.of('$L.toList()', stream) } as UnarySnippet)
        }

        @Override
        Optional<UnarySnippet> wrap() {
            wrappable
                    ? Optional.of({ scalar -> CodeBlock.of('$T.of($L)', List, scalar) } as UnarySnippet)
                    : Optional.empty()
        }

        @Override
        protected boolean matches(final TypeMirror type, final ResolveCtx c) {
            Containers.isList(type, c)
        }

        @Override
        protected TypeMirror element(final TypeMirror type, final ResolveCtx c) {
            Containers.typeArgument(type, 0)
        }

        @Override
        protected Optional<TypeElement> kindErasure(final ResolveCtx c) {
            hasKindErasure ? Optional.ofNullable(c.typeElementNamed('java.util.List')) : Optional.empty()
        }

        @Override
        protected TypeElement intermediateErasure(final ResolveCtx c) {
            c.typeElementNamed('java.util.stream.Stream')
        }
    }

    /** An Optional-shaped presence wrapper: matches Optional, element is its first type argument, no collect. */
    static class TestWrapper extends Container {

        boolean wrapNullable = false
        boolean hasKindErasure = true

        @Override
        Optional<UnarySnippet> iterate() {
            Optional.of({ container -> CodeBlock.of('$L.stream()', container) } as UnarySnippet)
        }

        @Override
        Optional<UnarySnippet> wrap() {
            Optional.of({ scalar -> CodeBlock.of('$T.ofNullable($L)', Optional, scalar) } as UnarySnippet)
        }

        @Override
        Optional<UnwrapSnippet> unwrap() {
            Optional.of({ wrapper, targetNullability ->
                targetNullability == Nullability.NULLABLE
                        ? CodeBlock.of('$L.orElse(null)', wrapper)
                        : CodeBlock.of('$L.orElseThrow()', wrapper)
            } as UnwrapSnippet)
        }

        @Override
        Optional<ScopeCodegen> mapPresence() {
            Optional.of({ operand, var, body -> CodeBlock.of('$L.map($N -> $L)', operand, var, body) } as ScopeCodegen)
        }

        @Override
        protected boolean matches(final TypeMirror type, final ResolveCtx c) {
            Containers.isOptional(type, c)
        }

        @Override
        protected TypeMirror element(final TypeMirror type, final ResolveCtx c) {
            Containers.typeArgument(type, 0)
        }

        @Override
        protected Nullability wrapNullness() {
            wrapNullable ? Nullability.NULLABLE : Nullability.NON_NULL
        }

        @Override
        protected Optional<TypeElement> kindErasure(final ResolveCtx c) {
            hasKindErasure ? Optional.ofNullable(c.typeElementNamed('java.util.Optional')) : Optional.empty()
        }

        @Override
        protected TypeElement intermediateErasure(final ResolveCtx c) {
            c.typeElementNamed('java.util.stream.Stream')
        }
    }

    /** A minimal container supplying none of the optional snippets — every optional operation stays absent. */
    static class TestBare extends Container {

        @Override
        protected boolean matches(final TypeMirror type, final ResolveCtx c) {
            Containers.isList(type, c)
        }

        @Override
        protected TypeMirror element(final TypeMirror type, final ResolveCtx c) {
            Containers.typeArgument(type, 0)
        }

        @Override
        protected Optional<TypeElement> kindErasure(final ResolveCtx c) {
            Optional.ofNullable(c.typeElementNamed('java.util.List'))
        }

        @Override
        protected TypeElement intermediateErasure(final ResolveCtx c) {
            c.typeElementNamed('java.util.stream.Stream')
        }
    }
}
