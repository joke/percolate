package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link SetContainer} unit-tested mock-only over the {@link ResolveCtx} type-query seam (change
 * {@code cutover-strategies-to-mock-seam}): every seam question is stubbed on a mocked {@code ResolveCtx}, and every
 * {@link TypeMirror}/{@link TypeElement} is an opaque, never-interrogated token compared only by identity. No javac.
 */
@Tag('unit')
class SetContainerSpec extends Specification {

    ResolveCtx ctx = Mock()
    TypeElement setElement = Mock()
    TypeElement streamElement = Mock()
    TypeMirror setOfString = Mock()
    TypeMirror streamOfString = Mock()
    TypeMirror stringType = Mock()
    TypeMirror streamRawType = Mock()
    TypeMirror listOfString = Mock()

    def 'iterates a Set into a Stream via .stream(), a plain operation with no child scope'() {
        ctx.isSet(streamOfString) >> false
        ctx.isDeclared(streamOfString) >> true
        ctx.erasure(streamOfString) >> streamOfString
        ctx.typeElementNamed('java.util.stream.Stream') >> streamElement
        streamElement.asType() >> streamRawType
        ctx.erasure(streamRawType) >> streamRawType
        ctx.isSameType(streamOfString, streamRawType) >> true
        ctx.typeArgument(streamOfString, 0) >> stringType
        ctx.isReferenceType(stringType) >> true
        ctx.typeElementNamed('java.util.Set') >> setElement
        ctx.declaredType(setElement, stringType) >> setOfString

        when:
        def specs = new SetContainer().expand(Demands.forTarget(streamOfString), ctx).toList()

        then:
        specs.size() == 1
        def iterate = specs[0]
        iterate.childScope.empty
        iterate.codegen instanceof OperationCodegen
        iterate.ports[0].type.is(setOfString)
        iterate.outputType.is(streamOfString)
        CodeBlock.of('$L\n', new SetContainer().iterate().get().render(CodeBlock.of('$N', 'xs'))).toString().contains('.stream()')
    }

    def 'collects a Stream into a Set (Collectors.toSet) and offers a plain single-element Set.of wrap'() {
        ctx.isSet(setOfString) >> true
        ctx.typeArgument(setOfString, 0) >> stringType
        ctx.isReferenceType(stringType) >> true
        ctx.typeElementNamed('java.util.stream.Stream') >> streamElement
        ctx.declaredType(streamElement, stringType) >> streamOfString

        when:
        def specs = new SetContainer().expand(Demands.forTarget(setOfString), ctx).toList()

        then: 'a plain collect Stream<String> -> Set<String>'
        def collect = specs.find { it.ports[0].type.is(streamOfString) }
        collect != null
        collect.childScope.empty
        collect.codegen instanceof OperationCodegen
        collect.weight == Weights.CONTAINER
        collect.outputType.is(setOfString)
        CodeBlock.of('$L\n', new SetContainer().collect().get().render(CodeBlock.of('$N', 's'))).toString().contains('toSet()')

        and: 'a plain single-element wrap String -> Set<String>'
        def wrap = specs.find { it.ports[0].type.is(stringType) }
        wrap != null
        wrap.childScope.empty
        wrap.codegen instanceof OperationCodegen
        wrap.outputType.is(setOfString)
    }

    def 'declines a target that is neither a Set nor a Stream'() {
        ctx.isSet(listOfString) >> false
        ctx.isDeclared(listOfString) >> false

        expect:
        new SetContainer().expand(Demands.forTarget(listOfString), ctx).toList().empty
    }

    def 'matches delegates the set-kind question to the seam'() {
        ctx.isSet(setOfString) >> true

        expect:
        new SetContainer().matches(setOfString, ctx)
    }

    def 'a non-set target does not match'() {
        ctx.isSet(setOfString) >> false

        expect:
        !new SetContainer().matches(setOfString, ctx)
    }

    def 'element extracts the sole type argument via the seam'() {
        ctx.typeArgument(setOfString, 0) >> stringType

        expect:
        new SetContainer().element(setOfString, ctx).is(stringType)
    }

    def 'factoryType is exactly java.util.Set, the single-element wrap factory and kind'() {
        expect:
        new SetContainer().factoryType() == Set
    }

    def 'collector renders Collectors.toSet() exactly'() {
        expect:
        new SetContainer().collector().toString() == 'java.util.stream.Collectors.toSet()'
    }

    def 'kindErasure resolves java.util.Set through the seam'() {
        ctx.typeElementNamed('java.util.Set') >> setElement

        expect:
        new SetContainer().kindErasure(ctx).get().is(setElement)
    }

    def 'wrap renders Set.of(...) around the scalar'() {
        expect:
        CodeBlock.of('$L\n', new SetContainer().wrap().get().render(CodeBlock.of('$N', 'x')))
                .toString().contains('Set.of(x)')
    }
}
