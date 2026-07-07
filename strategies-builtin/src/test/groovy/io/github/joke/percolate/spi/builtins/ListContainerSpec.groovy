package io.github.joke.percolate.spi.builtins

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link ListContainer} unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code cutover-strategies-to-mock-seam}): every seam question the container's expansion asks is stubbed on a
 * mocked {@code ResolveCtx}, and every {@link TypeMirror}/{@link TypeElement} is an opaque, never-interrogated
 * token compared only by identity. No javac. Folds the former {@code ListContainerSeamSpec} detection coverage in
 * alongside the full expansion coverage.
 */
@Tag('unit')
class ListContainerSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeElement listElement = Mock()
    TypeElement streamElement = Mock()
    TypeMirror listOfString = Mock()
    TypeMirror streamOfString = Mock()
    TypeMirror stringType = Mock()
    TypeMirror streamRawType = Mock()
    TypeMirror setOfString = Mock()

    def 'matches delegates the list-kind question to the seam'() {
        ctx.isList(listOfString) >> true

        expect:
        new ListContainer().matches(listOfString, ctx)
    }

    def 'a non-list target does not match'() {
        ctx.isList(listOfString) >> false

        expect:
        !new ListContainer().matches(listOfString, ctx)
    }

    def 'iterates a List into a Stream via .stream(), a plain operation with no child scope'() {
        ctx.isList(streamOfString) >> false
        ctx.isDeclared(streamOfString) >> true
        ctx.erasure(streamOfString) >> streamOfString
        ctx.typeElementNamed('java.util.stream.Stream') >> streamElement
        streamElement.asType() >> streamRawType
        ctx.erasure(streamRawType) >> streamRawType
        ctx.isSameType(streamOfString, streamRawType) >> true
        ctx.typeArgument(streamOfString, 0) >> stringType
        ctx.isReferenceType(stringType) >> true
        ctx.typeElementNamed('java.util.List') >> listElement
        ctx.declaredType(listElement, stringType) >> listOfString

        when:
        def specs = new ListContainer().expand(Demands.forTarget(streamOfString), ctx).toList()

        then:
        specs.size() == 1
        def iterate = specs[0]
        iterate.childScope.empty
        iterate.codegen instanceof OperationCodegen
        iterate.weight == Weights.CONTAINER
        iterate.ports[0].type.is(listOfString)
        iterate.outputType.is(streamOfString)
        new ListContainer().iterate().get().render(CodeBlock.of('$N', 'xs')).toString().contains('.stream()')
    }

    def 'collects a Stream into a List and offers a plain single-element List.of wrap'() {
        ctx.isList(listOfString) >> true
        ctx.typeArgument(listOfString, 0) >> stringType
        ctx.isReferenceType(stringType) >> true
        ctx.typeElementNamed('java.util.stream.Stream') >> streamElement
        ctx.declaredType(streamElement, stringType) >> streamOfString

        when:
        def specs = new ListContainer().expand(Demands.forTarget(listOfString), ctx).toList()

        then: 'a plain collect Stream<String> -> List<String>'
        def collect = specs.find { it.ports[0].type.is(streamOfString) }
        collect != null
        collect.childScope.empty
        collect.codegen instanceof OperationCodegen
        collect.outputType.is(listOfString)
        new ListContainer().collect().get().render(CodeBlock.of('$N', 's')).toString().contains('toList()')

        and: 'a plain single-element wrap String -> List<String>, no child scope'
        def wrap = specs.find { it.ports[0].type.is(stringType) }
        wrap != null
        wrap.childScope.empty
        wrap.codegen instanceof OperationCodegen
        wrap.outputType.is(listOfString)
        wrap.outputNullness == Nullability.NON_NULL

        and: 'no operation carries a child scope (no fused element mapping)'
        specs.every { it.childScope.empty }
    }

    def 'declines a target that is neither a List nor a Stream'() {
        ctx.isList(setOfString) >> false
        ctx.isDeclared(setOfString) >> false

        expect:
        new ListContainer().expand(Demands.forTarget(setOfString), ctx).toList().empty
    }
}
