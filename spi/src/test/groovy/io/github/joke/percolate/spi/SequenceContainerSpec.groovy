package io.github.joke.percolate.spi

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror

/**
 * Exercises the {@link SequenceContainer} base directly through a List-shaped test subclass: the base derives all
 * candidacy and emits only kind-local plain operations over an explicit {@code Stream<E>} (design D7) — never a
 * fused same-kind element mapping. A sequence iterates ({@code Cont<E> → Stream<E>}), collects
 * ({@code Stream<E> → Seq<E>}), and (when it has a single-element factory) wraps a scalar; the per-element transform
 * lives elsewhere (the generic stream strategy), so no spec here has a child scope.
 */
@Tag('unit')
class SequenceContainerSpec extends Specification {

    @Shared ResolveCtx ctx = ctx()
    @Shared TypeMirror listOfString = TypeUniverse.LIST_OF_STRING
    @Shared TypeMirror listOfInteger = TypeUniverse.LIST_OF_INT
    @Shared TypeMirror setOfString = decl('java.util.Set', TypeUniverse.STRING)
    @Shared TypeMirror streamOfString = decl('java.util.stream.Stream', TypeUniverse.STRING)

    def 'iterate: a Stream<E> demand from this container kind emits a plain iterate, no child scope'() {
        when:
        def specs = new TestSeq().bridge(listOfString, demand(streamOfString), ctx).toList()

        then:
        specs.size() == 1
        def iterate = specs[0]
        iterate.childScope.empty
        iterate.codegen instanceof OperationCodegen
        iterate.weight == Weights.CONTAINER
        iterate.ports.size() == 1
        ctx.types().isSameType(iterate.ports[0].type, listOfString)
        ctx.types().isSameType(iterate.outputType, streamOfString)
        !iterate.partial
    }

    def 'collect + wrap: this-kind target collects a Stream<E> and wraps a scalar, both plain'() {
        when:
        def specs = new TestSeq().bridge(streamOfString, demand(listOfString), ctx).toList()

        then: 'a plain collect Stream<String> -> List<String>'
        def collect = specs.find { ctx.types().isSameType(it.ports[0].type, streamOfString) }
        collect != null
        collect.childScope.empty
        collect.codegen instanceof OperationCodegen
        ctx.types().isSameType(collect.outputType, listOfString)

        and: 'a plain single-element wrap String -> List<String>'
        def wrap = specs.find { ctx.types().isSameType(it.ports[0].type, TypeUniverse.STRING) }
        wrap != null
        wrap.childScope.empty
        wrap.codegen instanceof OperationCodegen
        ctx.types().isSameType(wrap.outputType, listOfString)

        and: 'nothing carries a child scope (no fused element mapping)'
        specs.every { it.childScope.empty }
    }

    def 'a same-kind List<A> -> List<B> never emits an iterate (reshaping is left to the stream chain)'() {
        when:
        def specs = new TestSeq().bridge(listOfString, demand(listOfInteger), ctx).toList()

        then: 'only target-side collect/wrap, no iterate producing a Stream'
        specs.every { !Containers.isStream(it.outputType, ctx) }
        specs.every { it.childScope.empty }
    }

    def 'a container with no single-element factory collects but does not wrap'() {
        when:
        def specs = new TestSeq(wrappable: false).bridge(streamOfString, demand(listOfString), ctx).toList()

        then:
        specs.size() == 1
        ctx.types().isSameType(specs[0].ports[0].type, streamOfString)
        ctx.types().isSameType(specs[0].outputType, listOfString)
    }

    def 'declines when neither side is this container kind'() {
        expect:
        new TestSeq().bridge(TypeUniverse.STRING, demand(setOfString), ctx).toList().empty
    }

    private static Demand demand(final TypeMirror target, final Nullability nullness = Nullability.NON_NULL) {
        [
                targetType     : { target },
                targetNullness : { nullness },
                directive      : { Optional.empty() },
                declaredChildren: { [] as Set },
                candidates     : { [] },
                nullnessOf     : { TypeMirror t, Element s -> Nullability.NON_NULL },
        ] as Demand
    }

    private static TypeMirror decl(final String fqn, final TypeMirror arg) {
        TypeUniverse.types().getDeclaredType(TypeUniverse.elements().getTypeElement(fqn), arg)
    }

    private static ResolveCtx ctx() {
        [
                elements       : { TypeUniverse.elements() },
                types          : { TypeUniverse.types() },
                mapperType     : { null },
                currentMethod  : { null },
                callableMethods: { null },
        ] as ResolveCtx
    }

    /** A List-shaped sequence: matches List, element is its first type argument, optionally has a List.of wrap. */
    static class TestSeq extends SequenceContainer {

        boolean wrappable = true

        @Override
        CodeBlock iterate(final CodeBlock container) {
            CodeBlock.of('$L.stream()', container)
        }

        @Override
        CodeBlock collect(final CodeBlock stream) {
            CodeBlock.of('$L.toList()', stream)
        }

        @Override
        protected boolean matches(final TypeMirror type, final ResolveCtx c) {
            Containers.isList(type, c)
        }

        @Override
        protected TypeMirror element(final TypeMirror type) {
            Containers.typeArgument(type, 0)
        }

        @Override
        protected Optional<OperationCodegen> singleElementWrap() {
            wrappable
                    ? Optional.of({ vars, inputs -> CodeBlock.of('$T.of($L)', List, inputs.single()) } as OperationCodegen)
                    : Optional.empty()
        }
    }
}
