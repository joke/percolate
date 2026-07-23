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
 * {@link MonoBlock} unit-tested mock-only over the {@link ResolveCtx} type-query seam: every seam question is a
 * validated, strictly-verified interaction on a mocked {@code ResolveCtx}, and every
 * {@link TypeMirror}/{@link TypeElement} is an opaque, never-interrogated token compared only by identity. No javac.
 */
@Tag('unit')
class MonoBlockSpec extends Specification {

    def 'expand() is empty when the demanded target is not a blockable scalar (not a declared type)'() {
        ResolveCtx ctx = Mock()
        TypeMirror to = Mock()

        when:
        def specs = new MonoBlock().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isDeclared(to) >> false
        0 * _

        expect:
        specs.empty
    }

    def 'expand() is empty when the demanded target is itself reactive (a Mono)'() {
        ResolveCtx ctx = Mock()
        TypeMirror to = Mock()

        when:
        def specs = new MonoBlock().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isDeclared(to) >> true
        1 * ctx.isType(to, Blockings.MONO) >> true
        0 * _

        expect:
        specs.empty
    }

    def 'expand() is empty when Mono is not on the compile classpath'() {
        ResolveCtx ctx = Mock()
        TypeMirror to = Mock()

        when:
        def specs = new MonoBlock().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isDeclared(to) >> true
        1 * ctx.isType(to, Blockings.MONO) >> false
        1 * ctx.isType(to, Blockings.FLUX) >> false
        1 * ctx.typeElementNamed(Blockings.MONO) >> null
        0 * _

        expect:
        specs.empty
    }

    def 'expand() offers a reuse-only partial block() production for a demanded scalar T'() {
        ResolveCtx ctx = Mock()
        TypeMirror to = Mock()
        TypeElement monoElement = Mock()
        TypeMirror monoOfTo = Mock()

        when:
        def specs = new MonoBlock().expand(Demands.forTarget(to), ctx).toList()

        then:
        1 * ctx.isDeclared(to) >> true
        1 * ctx.isType(to, Blockings.MONO) >> false
        1 * ctx.isType(to, Blockings.FLUX) >> false
        1 * ctx.typeElementNamed(Blockings.MONO) >> monoElement
        1 * ctx.declaredType(monoElement, to) >> monoOfTo
        0 * _

        expect:
        specs.size() == 1
        def spec = specs[0]
        spec.label == 'block'
        spec.partial
        spec.weight == Blockings.WEIGHT
        spec.ports == [Port.reuse('mono', monoOfTo, Nullability.NON_NULL)]
        spec.outputType.is(to)
        spec.outputNullness == Nullability.NON_NULL
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        CodeBlock.of('$L\n', spec.codegen.render(singleInput(CodeBlock.of('$N', 'src')))).toString().contains('.block()')
    }
}
