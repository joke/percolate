package io.github.joke.percolate.spi

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror

/**
 * Exercises the {@link WrapperContainer} base directly through an Optional-shaped test subclass. A presence wrapper
 * emits only kind-local operations (design D7): a plain {@code wrap}, a same-kind scope-owning {@code mapPresence}
 * (the only child-scope operation a wrapper has — it has no {@code collect}), a plain {@code iterate} yielding the
 * 0-or-1 element stream that realises drop-empties, and a plain <b>partial</b> {@code unwrap}. No operation knows
 * any other container kind.
 */
@Tag('unit')
class WrapperContainerSpec extends Specification {

    @Shared ResolveCtx ctx = ctx()
    @Shared TypeMirror optionalOfString = decl('java.util.Optional', TypeUniverse.STRING)
    @Shared TypeMirror optionalOfInteger = decl('java.util.Optional', TypeUniverse.INTEGER)
    @Shared TypeMirror streamOfString = decl('java.util.stream.Stream', TypeUniverse.STRING)
    @Shared TypeMirror streamOfInteger = decl('java.util.stream.Stream', TypeUniverse.INTEGER)

    def 'wrap + mapPresence: Optional<A> -> Optional<B> wraps a scalar and maps presence in its own kind'() {
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

    def 'wrap only: a scalar source for an Optional<E> target emits a plain wrap and nothing else'() {
        when:
        def specs = new TestWrapper().bridge(TypeUniverse.STRING, demand(optionalOfString), ctx).toList()

        then:
        specs.size() == 1
        specs[0].childScope.empty
        ctx.types().isSameType(specs[0].ports[0].type, TypeUniverse.STRING)
        ctx.types().isSameType(specs[0].outputType, optionalOfString)
    }

    def 'iterate: a Stream<E> demand from Optional<E> emits a plain 0-or-1 iterate, no unwrap'() {
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

    def 'unwrap: a scalar demand from Optional<E> emits a plain partial unwrap under the demanded nullness'() {
        when:
        def specs = new TestWrapper().bridge(optionalOfString, demand(TypeUniverse.STRING, Nullability.NULLABLE), ctx).toList()

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

    def 'a Stream<other> demand from Optional<E> emits nothing (element types differ)'() {
        expect:
        new TestWrapper().bridge(optionalOfString, demand(streamOfInteger), ctx).toList().empty
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

    /** An Optional-shaped wrapper: matches Optional, element is its first type argument. */
    static class TestWrapper extends WrapperContainer {

        @Override
        CodeBlock iterate(final CodeBlock container) {
            CodeBlock.of('$L.stream()', container)
        }

        @Override
        CodeBlock mapPresence(final CodeBlock wrapper, final String var, final CodeBlock body) {
            CodeBlock.of('$L.map($N -> $L)', wrapper, var, body)
        }

        @Override
        CodeBlock wrap(final CodeBlock scalar) {
            CodeBlock.of('$T.ofNullable($L)', Optional, scalar)
        }

        @Override
        CodeBlock unwrap(final CodeBlock wrapper, final Nullability targetNullability) {
            targetNullability == Nullability.NULLABLE
                    ? CodeBlock.of('$L.orElse(null)', wrapper)
                    : CodeBlock.of('$L.orElseThrow()', wrapper)
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
