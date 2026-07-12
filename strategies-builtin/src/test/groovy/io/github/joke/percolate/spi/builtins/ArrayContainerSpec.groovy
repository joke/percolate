package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.javapoet.CodeBlock
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link ArrayContainer} unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code cutover-strategies-to-mock-seam}): every seam question is stubbed on a mocked {@code ResolveCtx}, and every
 * {@link TypeMirror}/{@link TypeElement} is an opaque, never-interrogated token compared only by identity. No javac.
 */
@Tag('unit')
class ArrayContainerSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeElement streamElement = Mock()
    TypeMirror stringArray = Mock()
    TypeMirror streamOfString = Mock()
    TypeMirror stringType = Mock()
    TypeMirror streamRawType = Mock()

    def 'iterates an array into a Stream via Arrays.stream, a plain operation with no child scope'() {
        ctx.isArray(streamOfString) >> false
        ctx.isDeclared(streamOfString) >> true
        ctx.erasure(streamOfString) >> streamOfString
        ctx.typeElementNamed('java.util.stream.Stream') >> streamElement
        streamElement.asType() >> streamRawType
        ctx.erasure(streamRawType) >> streamRawType
        ctx.isSameType(streamOfString, streamRawType) >> true
        ctx.typeArgument(streamOfString, 0) >> stringType
        ctx.arrayType(stringType) >> stringArray

        when:
        def specs = new ArrayContainer().expand(Demands.forTarget(streamOfString), ctx).toList()

        then:
        specs.size() == 1
        def iterate = specs[0]
        iterate.childScope.empty
        iterate.codegen instanceof OperationCodegen
        iterate.weight == Weights.CONTAINER
        iterate.ports[0].type.is(stringArray)
        iterate.outputType.is(streamOfString)
        new ArrayContainer().iterate().get().render(CodeBlock.of('$N', 'a')).toString().contains('Arrays.stream')
    }

    def 'collects a Stream into an array and offers no single-element wrap (arrays have no factory)'() {
        ctx.isArray(stringArray) >> true
        ctx.arrayComponent(stringArray) >> stringType
        ctx.isReferenceType(stringType) >> true
        ctx.typeElementNamed('java.util.stream.Stream') >> streamElement
        ctx.declaredType(streamElement, stringType) >> streamOfString
        ctx.isDeclared(stringArray) >> false

        when:
        def specs = new ArrayContainer().expand(Demands.forTarget(stringArray), ctx).toList()

        then:
        specs.size() == 1
        def collect = specs[0]
        collect.childScope.empty
        collect.codegen instanceof OperationCodegen
        collect.ports[0].type.is(streamOfString)
        collect.outputType.is(stringArray)
        CodeBlock.of('$L\n', new ArrayContainer().collect().get().render(CodeBlock.of('$N', 's'))).toString().contains('toArray()')
    }

    def 'declines a target that is neither an array nor a Stream'() {
        ctx.isArray(stringType) >> false
        ctx.isDeclared(stringType) >> false

        expect:
        new ArrayContainer().expand(Demands.forTarget(stringType), ctx).toList().empty
    }
}
