package io.github.joke.percolate.spi

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * Exercises the {@link Accessor} archetype base through a tiny authored subclass. The base is directive-pinned: it
 * reads the single source-path segment and the parent type from the demand, declines a non-declared parent or a
 * non-single-segment directive, and wires the one-port accessor {@link OperationSpec} (typing the produced nullness
 * through the demand oracle). The author supplies only {@link Accessor#accessor}: the member match and its rendering.
 */
@Tag('unit')
class AccessorSpec extends Specification {

    @Shared ResolveCtx ctx = ctx()

    def 'the base wires a one-port accessor spec from a single-segment directive on a declared parent'() {
        when:
        def specs = new TestAccessor().expand(descend(TypeUniverse.STRING, 'known'), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.label == 'known()'
        spec.weight == 7
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.ports.size() == 1
        spec.ports[0].name == 'value'
        !spec.ports[0].reuseOnly
        ctx.types().isSameType(spec.ports[0].type, TypeUniverse.STRING)
        spec.ports[0].nullness == Nullability.NON_NULL
        ctx.types().isSameType(spec.outputType, TypeUniverse.INTEGER)
        spec.outputNullness == Nullability.NULLABLE
    }

    def 'no directive yields no accessor'() {
        expect:
        new TestAccessor().expand(bare(TypeUniverse.STRING), ctx).toList().empty
    }

    def 'a multi-segment directive yields no accessor (only a single segment is resolved)'() {
        expect:
        new TestAccessor().expand(descend(TypeUniverse.STRING, 'a', 'b'), ctx).toList().empty
    }

    def 'a non-declared parent yields no accessor'() {
        expect:
        new TestAccessor().expand(descend(TypeUniverse.INT, 'known'), ctx).toList().empty
    }

    def 'an unresolved segment yields no accessor'() {
        expect:
        new TestAccessor().expand(descend(TypeUniverse.STRING, 'missing'), ctx).toList().empty
    }

    private static Demand descend(final TypeMirror parent, final String... segments) {
        demand(parent, directive(segments.toList()))
    }

    private static Demand bare(final TypeMirror parent) {
        demand(parent, null)
    }

    private static Demand demand(final TypeMirror parent, final Directive directive) {
        [
                targetType      : { parent },
                targetNullness  : { Nullability.NON_NULL },
                directive       : { Optional.ofNullable(directive) },
                declaredChildren: { [] as Set },
                bindingName     : { '' },
                nullnessOf      : { TypeMirror t, Element s -> Nullability.NULLABLE },
        ] as Demand
    }

    private static Directive directive(final List<String> sourcePath) {
        [
                sourcePath  : { sourcePath },
                constant    : { Optional.empty() },
                defaultValue: { Optional.empty() },
        ] as Directive
    }

    private static ResolveCtx ctx() {
        [
                elements       : { TypeUniverse.elements() },
                types          : { TypeUniverse.types() },
                callableMethods: { null },
        ] as ResolveCtx
    }

    /** Resolves only the segment {@code known}, producing an Integer-typed access rendered as {@code parent.known()}. */
    static class TestAccessor extends Accessor {
        @Override
        protected Optional<Step> accessor(final TypeElement parent, final String segment, final ResolveCtx c) {
            if (segment != 'known') {
                return Optional.empty()
            }
            def codegen = { IncomingValues inputs -> CodeBlock.of('$L.known()', inputs.single()) } as OperationCodegen
            Optional.of(new Step(TypeUniverse.INTEGER, parent, 'known()', 7, codegen))
        }
    }
}
