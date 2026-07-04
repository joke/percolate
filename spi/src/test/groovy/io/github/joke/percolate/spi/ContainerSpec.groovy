package io.github.joke.percolate.spi

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import io.github.joke.percolate.spi.types.TypeRef
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * Exercises the single {@link Container} base through a sequence-shaped and a wrapper-shaped test subclass. The base is
 * fully <b>target-driven</b> (design D1/D8): every operation is keyed on the demanded target alone — no candidate is
 * read. Container kind is <b>emergent from which operations are supplied</b>: a sequence supplies {@code collect}; a
 * presence wrapper does not (supplying {@code wrap}/{@code unwrap}/{@code mapPresence} instead). The per-element
 * transform over the intermediate lives in the generic stream strategy, so no plain operation here carries a child
 * scope — only a wrapper's same-kind {@code mapPresence}, emitted as a type-variable functor lift.
 */
@Tag('unit')
class ContainerSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()
    @Shared ResolveCtx ctx = ctx()
    @Shared TypeMirror listOfString = javac.LIST_OF_STRING
    @Shared TypeMirror listOfInteger = javac.LIST_OF_INT
    @Shared TypeMirror setOfString = decl('java.util.Set', javac.STRING)
    @Shared TypeMirror optionalOfString = decl('java.util.Optional', javac.STRING)
    @Shared TypeMirror streamOfString = decl('java.util.stream.Stream', javac.STRING)

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
        ctx.types().isSameType(iterate.ports[0].type, listOfString)
        ctx.types().isSameType(iterate.outputType, streamOfString)
        !iterate.partial
    }

    def 'a sequence target collects a Stream<E> and wraps a scalar, both plain'() {
        when:
        def specs = new TestSeq().expand(demand(listOfString), ctx).toList()

        then: 'a plain collect Stream<String> -> List<String>'
        def collect = specs.find { ctx.types().isSameType(it.ports[0].type, streamOfString) }
        collect != null
        collect.childScope.empty
        collect.codegen instanceof OperationCodegen
        ctx.types().isSameType(collect.outputType, listOfString)

        and: 'a plain single-element wrap String -> List<String>'
        def wrap = specs.find { ctx.types().isSameType(it.ports[0].type, javac.STRING) }
        wrap != null
        wrap.childScope.empty
        ctx.types().isSameType(wrap.outputType, listOfString)

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
        ctx.types().isSameType(specs[0].ports[0].type, streamOfString)
        ctx.types().isSameType(specs[0].outputType, listOfString)
    }

    def 'a sequence declines a target that is neither its kind nor its intermediate'() {
        expect:
        new TestSeq().expand(demand(setOfString), ctx).toList().empty
    }

    // ---- presence wrapper (collect absent) ----------------------------------------------------------------

    def 'a wrapper wraps a scalar and maps presence in its own kind, as a functor lift'() {
        when:
        def specs = new TestWrapper().expand(demand(optionalOfString), ctx).toList()

        then: 'a plain wrap String -> Optional<String>, no child scope'
        def wrap = specs.find { it.childScope.empty && ctx.types().isSameType(it.ports[0].type, javac.STRING) }
        wrap != null
        wrap.codegen instanceof OperationCodegen
        ctx.types().isSameType(wrap.outputType, optionalOfString)

        and: 'a scope-owning functor-lift mapPresence Optional<A> -> Optional<String> over a type-variable port'
        def mapping = specs.find { it.childScope.present }
        mapping != null
        mapping.codegen instanceof ScopeCodegen
        mapping.ports[0].template == TypeRef.declared('java.util.Optional', TypeRef.variable('V0'))
        def child = mapping.childScope.get()
        child.elementInTemplate == TypeRef.variable('V0')
        ctx.types().isSameType(child.elementOut, javac.STRING)
        ctx.types().isSameType(mapping.outputType, optionalOfString)
        !mapping.partial
    }

    def 'a wrapper iterates a 0-or-1 Stream<E> from its own kind and supplies no collect'() {
        when:
        def specs = new TestWrapper().expand(demand(streamOfString), ctx).toList()

        then: 'the iterate produces Stream<String> from Optional<String> (the wrapper of the stream element)'
        def iterate = specs.find { ctx.types().isSameType(it.ports[0].type, decl('java.util.Optional', javac.STRING)) }
        iterate != null
        iterate.childScope.empty
        iterate.codegen instanceof OperationCodegen
        ctx.types().isSameType(iterate.outputType, streamOfString)
        !iterate.partial
        specs.every { !it.childScope.present }
    }

    def 'a wrapper unwraps a scalar target, plain and partial, under the demanded nullness'() {
        when:
        def specs = new TestWrapper().expand(demand(javac.STRING, Nullability.NULLABLE), ctx).toList()

        then:
        specs.size() == 1
        def unwrap = specs[0]
        unwrap.childScope.empty
        unwrap.codegen instanceof OperationCodegen
        unwrap.partial
        ctx.types().isSameType(unwrap.ports[0].type, optionalOfString)
        ctx.types().isSameType(unwrap.outputType, javac.STRING)
        unwrap.outputNullness == Nullability.NULLABLE
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

    private TypeMirror decl(final String fqn, final TypeMirror arg) {
        javac.types().getDeclaredType(javac.elements().getTypeElement(fqn), arg)
    }

    private ResolveCtx ctx() {
        [
                elements       : { javac.elements() },
                types          : { javac.types() },
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

        @Override
        protected Optional<TypeElement> kindErasure(final ResolveCtx c) {
            Optional.ofNullable(c.elements().getTypeElement('java.util.List'))
        }

        @Override
        protected TypeElement intermediateErasure(final ResolveCtx c) {
            c.elements().getTypeElement('java.util.stream.Stream')
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

        @Override
        protected Optional<TypeElement> kindErasure(final ResolveCtx c) {
            Optional.ofNullable(c.elements().getTypeElement('java.util.Optional'))
        }

        @Override
        protected TypeElement intermediateErasure(final ResolveCtx c) {
            c.elements().getTypeElement('java.util.stream.Stream')
        }
    }
}
