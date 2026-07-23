package io.github.joke.percolate.reactorblocking

import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.reactorblocking.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

import static io.github.joke.percolate.reactorblocking.test.Codegens.singleInput

/**
 * {@link FluxCollectListBlock} unit-tested mock-only over the {@link ResolveCtx} type-query seam: every seam
 * question is a validated, strictly-verified interaction on a mocked {@code ResolveCtx}, and every
 * {@link TypeMirror}/{@link TypeElement} is an opaque, never-interrogated token compared only by identity. No javac.
 */
@Tag('unit')
class FluxCollectListBlockSpec extends Specification {

    def 'expand() is empty when the demanded target is not a List'() {
        ResolveCtx ctx = Mock()
        TypeMirror to = Mock()

        when:
        def specs = new FluxCollectListBlock().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isList(to) >> false
        0 * _

        expect:
        specs.empty
    }

    def 'expand() is empty when Flux is not on the compile classpath'() {
        ResolveCtx ctx = Mock()
        TypeMirror to = Mock()
        TypeMirror element = Mock()

        when:
        def specs = new FluxCollectListBlock().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isList(to) >> true
        1 * ctx.typeArgument(to, 0) >> element
        1 * ctx.typeElementNamed(Blockings.FLUX) >> null
        0 * _

        expect:
        specs.empty
    }

    def 'expand() offers a reuse-only collectList().block() production for a demanded List<T>'() {
        ResolveCtx ctx = Mock()
        TypeMirror to = Mock()
        TypeMirror element = Mock()
        TypeElement fluxElement = Mock()
        TypeMirror fluxOfElement = Mock()

        when:
        def specs = new FluxCollectListBlock().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isList(to) >> true
        1 * ctx.typeArgument(to, 0) >> element
        1 * ctx.typeElementNamed(Blockings.FLUX) >> fluxElement
        1 * ctx.declaredType(fluxElement, element) >> fluxOfElement
        0 * _

        expect:
        specs.size() == 1
        def spec = specs[0]
        spec.label == 'collectList().block'
        !spec.partial
        spec.weight == Blockings.WEIGHT
        spec.ports == [Port.reuse('flux', fluxOfElement, Nullability.NON_NULL)]
        spec.outputType.is(to)
        spec.outputNullness == Nullability.NON_NULL
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        def rendered = CodeBlock.of('$L\n', spec.codegen.render(singleInput(CodeBlock.of('$N', 'src')))).toString()
        rendered.contains('.collectList()')
        rendered.contains('.block()')
    }
}
