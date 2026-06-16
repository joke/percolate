package io.github.joke.percolate.spi

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror

/**
 * Exercises the single {@link Container} base through a sequence-shaped and a wrapper-shaped test subclass. Container
 * kind is <b>emergent from which operations are supplied</b>: a sequence supplies {@code collect}; a presence wrapper
 * does not (supplying {@code wrap}/{@code unwrap}/{@code mapPresence} instead). The base emits only kind-local plain
 * operations over an explicit {@code Stream<E>} (design D7) — the per-element transform lives in the generic stream
 * strategy, so no plain operation here carries a child scope (only a wrapper's same-kind {@code mapPresence} does).
 */
@Tag('unit')
class ContainerSpec extends Specification {

    @Shared ResolveCtx ctx = ctx()
    @Shared TypeMirror listOfString = TypeUniverse.LIST_OF_STRING
    @Shared TypeMirror listOfInteger = TypeUniverse.LIST_OF_INT
    @Shared TypeMirror setOfString = decl('java.util.Set', TypeUniverse.STRING)
    @Shared TypeMirror optionalOfString = decl('java.util.Optional', TypeUniverse.STRING)
    @Shared TypeMirror optionalOfInteger = decl('java.util.Optional', TypeUniverse.INTEGER)
    @Shared TypeMirror streamOfString = decl('java.util.stream.Stream', TypeUniverse.STRING)
    @Shared TypeMirror streamOfInteger = decl('java.util.stream.Stream', TypeUniverse.INTEGER)

    // ---- kind is emergent ----------------------------------------------------------------------------------

    def 'a container supplying collect is a sequence; one omitting it is a presence wrapper'() {
        expect:
        new TestSeq().collect().present
        !new TestWrapper().collect().present
        new TestWrapper().mapPresence().present
        !new TestSeq().mapPresence().present
    }

    // ---- sequence (collect present) ------------------------------------------------------------------------

    def 'a sequence iterates a Stream<E>, plain, no child scope'() {
        when:
        def specs = new TestSeq().bridge(listOfString, demand(streamOfString), ctx).toList()

        then:
        specs.size() == 1
        def iterate = specs[0]
        iterate.childScope.empty
        iterate.codegen instanceof OperationCodegen
        iterate.weight == Weights.CONTAINER
        ctx.types().isSameType(iterate.ports[0].type, listOfString)
        ctx.types().isSameType(iterate.outputType, streamOfString)
        !iterate.partial
    }

    def 'a sequence collects a Stream<E> and wraps a scalar, both plain'() {
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
        ctx.types().isSameType(wrap.outputType, listOfString)

        and: 'nothing carries a child scope (no fused element mapping)'
        specs.every { it.childScope.empty }
    }

    def 'a same-kind List<A> -> List<B> never emits an iterate (reshaping is left to the stream chain)'() {
        when:
        def specs = new TestSeq().bridge(listOfString, demand(listOfInteger), ctx).toList()

        then:
        specs.every { !Containers.isStream(it.outputType, ctx) }
        specs.every { it.childScope.empty }
    }

    def 'a sequence with no single-element factory collects but does not wrap'() {
        when:
        def specs = new TestSeq(wrappable: false).bridge(streamOfString, demand(listOfString), ctx).toList()

        then:
        specs.size() == 1
        ctx.types().isSameType(specs[0].ports[0].type, streamOfString)
        ctx.types().isSameType(specs[0].outputType, listOfString)
    }

    def 'a sequence declines when neither side is its kind'() {
        expect:
        new TestSeq().bridge(TypeUniverse.STRING, demand(setOfString), ctx).toList().empty
    }

    // ---- presence wrapper (collect absent) ----------------------------------------------------------------

    def 'a wrapper wraps a scalar and maps presence in its own kind'() {
        when:
        def specs = new TestWrapper().bridge(optionalOfInteger, demand(optionalOfString), ctx).toList()

        then: 'a plain wrap String -> Optional<String>, no child scope'
        def wrap = specs.find { it.childScope.empty }
        wrap != null
        wrap.codegen instanceof OperationCodegen
        ctx.types().isSameType(wrap.ports[0].type, TypeUniverse.STRING)
        ctx.types().isSameType(wrap.outputType, optionalOfString)

        and: 'a scope-owning mapPresence Optional<Integer> -> Optional<String>'
        def mapping = specs.find { it.childScope.present }
        mapping != null
        mapping.codegen instanceof ScopeCodegen
        def child = mapping.childScope.get()
        ctx.types().isSameType(child.elementIn, TypeUniverse.INTEGER)
        ctx.types().isSameType(child.elementOut, TypeUniverse.STRING)
        ctx.types().isSameType(mapping.ports[0].type, optionalOfInteger)
        ctx.types().isSameType(mapping.outputType, optionalOfString)
        !mapping.partial
    }

    def 'a wrapper iterates a 0-or-1 Stream<E> and supplies no collect'() {
        when:
        def specs = new TestWrapper().bridge(optionalOfString, demand(streamOfString), ctx).toList()

        then:
        specs.size() == 1
        def iterate = specs[0]
        iterate.childScope.empty
        iterate.codegen instanceof OperationCodegen
        ctx.types().isSameType(iterate.ports[0].type, optionalOfString)
        ctx.types().isSameType(iterate.outputType, streamOfString)
        !iterate.partial
    }

    def 'a wrapper unwraps to a scalar, plain and partial, under the demanded nullness'() {
        when:
        def specs = new TestWrapper().bridge(optionalOfString, demand(TypeUniverse.STRING, Nullability.NULLABLE), ctx)
                .toList()

        then:
        specs.size() == 1
        def unwrap = specs[0]
        unwrap.childScope.empty
        unwrap.codegen instanceof OperationCodegen
        unwrap.partial
        ctx.types().isSameType(unwrap.ports[0].type, optionalOfString)
        ctx.types().isSameType(unwrap.outputType, TypeUniverse.STRING)
        unwrap.outputNullness == Nullability.NULLABLE
    }

    def 'a wrapper declines a Stream<other> demand (element types differ)'() {
        expect:
        new TestWrapper().bridge(optionalOfString, demand(streamOfInteger), ctx).toList().empty
    }

    private static Demand demand(final TypeMirror target, final Nullability nullness = Nullability.NON_NULL) {
        [
                targetType      : { target },
                targetNullness  : { nullness },
                directive       : { Optional.empty() },
                declaredChildren: { [] as Set },
                bindingName     : { '' },
                candidates      : { [] },
                nullnessOf      : { TypeMirror t, Element s -> Nullability.NON_NULL },
        ] as Demand
    }

    private static TypeMirror decl(final String fqn, final TypeMirror arg) {
        TypeUniverse.types().getDeclaredType(TypeUniverse.elements().getTypeElement(fqn), arg)
    }

    private static ResolveCtx ctx() {
        [
                elements       : { TypeUniverse.elements() },
                types          : { TypeUniverse.types() },
                callableMethods: { null },
        ] as ResolveCtx
    }

    /** A List-shaped sequence: matches List, element is its first type argument, optionally has a List.of wrap. */
    static class TestSeq extends Container {

        boolean wrappable = true

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
        protected TypeMirror element(final TypeMirror type) {
            Containers.typeArgument(type, 0)
        }
    }

    /** An Optional-shaped presence wrapper: matches Optional, element is its first type argument, no collect. */
    static class TestWrapper extends Container {

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
        protected TypeMirror element(final TypeMirror type) {
            Containers.typeArgument(type, 0)
        }
    }
}
