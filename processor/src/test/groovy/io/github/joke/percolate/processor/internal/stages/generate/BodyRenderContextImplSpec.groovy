package io.github.joke.percolate.processor.internal.stages.generate

import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.Operation
import io.github.joke.percolate.processor.internal.graph.Value
import io.github.joke.percolate.spi.MemberRequest
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.SwitchStyle
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.SourceVersion
import javax.lang.model.type.TypeMirror

/**
 * {@link BodyRenderContextImpl} unit-tested directly: {@code buildFor} gathers a {@code BodyCodegen} operation's
 * port operands and grounded types the same way {@code Walk#renderPlain} gathers an {@code OperationCodegen}'s, and
 * the instance otherwise exposes the {@link ResolveCtx}/{@link SwitchStyle}/{@link SourceVersion} it was built with.
 */
@Tag('unit')
class BodyRenderContextImplSpec extends Specification {

    BodyRenderContextFactory bodyRenderContextFactory = new BodyRenderContextFactory()

    MapperGraph graph = Mock()
    MemberPlan memberPlan = Mock()
    ResolveCtx resolveCtx = Mock()
    SwitchStyle switchStyle = SwitchStyle.CLASSIC
    SourceVersion sourceVersion = SourceVersion.RELEASE_17

    def 'buildFor gathers one port operand and grounded type per port, positional and by name'() {
        Operation operation = Mock()
        def port0 = new Port('a', Mock(TypeMirror), Nullability.NON_NULL)
        def port1 = new Port('b', Mock(TypeMirror), Nullability.NON_NULL)
        Value source0 = Mock()
        Value source1 = Mock()
        TypeMirror type0 = Mock()
        TypeMirror type1 = Mock()

        when:
        def context = bodyRenderContextFactory.buildFor(graph, operation,
                { Value v -> v.is(source0) ? CodeBlock.of('x') : CodeBlock.of('y') }, memberPlan, resolveCtx,
                switchStyle, sourceVersion)

        then:
        1 * operation.ports >> [port0, port1]
        1 * graph.portSource(operation, 'a') >> Optional.of(source0)
        1 * graph.portSource(operation, 'b') >> Optional.of(source1)
        1 * source0.type >> Optional.of(type0)
        1 * source1.type >> Optional.of(type1)
        1 * operation.memberRequests >> []
        0 * _

        expect:
        context.byGroupPosition(0).toString() == 'x'
        context.byGroupPosition(1).toString() == 'y'
        context.byName('a').toString() == 'x'
        context.byName('b').toString() == 'y'
        context.portType('a').is(type0)
        context.portType('b').is(type1)
    }

    def 'buildFor resolves a member reference for a strategy-requested member, by dedup key'() {
        Operation operation = Mock()
        def memberRequest = new MemberRequest(null, CodeBlock.of('null'), 'fmt-yyyy-MM-dd')

        when:
        def context = bodyRenderContextFactory.buildFor(graph, operation, { Value v -> CodeBlock.of('x') }, memberPlan,
                resolveCtx, switchStyle, sourceVersion)

        then:
        1 * operation.ports >> []
        1 * operation.memberRequests >> [memberRequest]
        1 * memberPlan.reference('fmt-yyyy-MM-dd') >> CodeBlock.of('FMT')
        0 * _

        expect:
        context.member('fmt-yyyy-MM-dd').toString() == 'FMT'
    }

    def 'buildFor fails fast when a port has no source'() {
        Operation operation = Mock()
        def port = new Port('a', Mock(TypeMirror), Nullability.NON_NULL)

        when:
        bodyRenderContextFactory.buildFor(graph, operation, { Value v -> CodeBlock.of('x') }, memberPlan, resolveCtx,
                switchStyle, sourceVersion)

        then:
        1 * operation.ports >> [port]
        1 * graph.portSource(operation, 'a') >> Optional.empty()
        0 * _
        def error = thrown(IllegalStateException)

        expect:
        error.message.contains('a')
    }

    def 'buildFor fails fast when a port source has no type'() {
        Operation operation = Mock()
        def port = new Port('a', Mock(TypeMirror), Nullability.NON_NULL)
        Value source = Mock()

        when:
        bodyRenderContextFactory.buildFor(graph, operation, { Value v -> CodeBlock.of('x') }, memberPlan, resolveCtx,
                switchStyle, sourceVersion)

        then:
        1 * operation.ports >> [port]
        1 * graph.portSource(operation, 'a') >> Optional.of(source)
        1 * source.type >> Optional.empty()
        0 * _
        def error = thrown(IllegalStateException)

        expect:
        error.message.contains('a')
    }

    def 'portType fails fast for a port name the context was not built with'() {
        Operation operation = Mock()

        when:
        def context = bodyRenderContextFactory.buildFor(graph, operation, { Value v -> CodeBlock.of('x') }, memberPlan,
                resolveCtx, switchStyle, sourceVersion)

        then:
        1 * operation.ports >> []
        1 * operation.memberRequests >> []
        0 * _

        when:
        context.portType('ghost')

        then:
        def error = thrown(IllegalStateException)
        error.message.contains('ghost')
    }

    def 'exposes the resolveCtx, switchStyle, and sourceVersion it was built with'() {
        Operation operation = Mock()

        when:
        def context = bodyRenderContextFactory.buildFor(graph, operation, { Value v -> CodeBlock.of('x') }, memberPlan,
                resolveCtx, switchStyle, sourceVersion)

        then:
        1 * operation.ports >> []
        1 * operation.memberRequests >> []
        0 * _

        expect:
        context.resolveCtx().is(resolveCtx)
        context.switchStyle() == SwitchStyle.CLASSIC
        context.sourceVersion() == SourceVersion.RELEASE_17
    }

    def 'single delegates to the composed IncomingValues'() {
        Operation operation = Mock()
        Value source = Mock()
        TypeMirror type = Mock()
        def port = new Port('value', Mock(TypeMirror), Nullability.NON_NULL)

        when:
        def context = bodyRenderContextFactory.buildFor(graph, operation, { Value v -> CodeBlock.of('x') }, memberPlan,
                resolveCtx, switchStyle, sourceVersion)

        then:
        1 * operation.ports >> [port]
        1 * graph.portSource(operation, 'value') >> Optional.of(source)
        1 * source.type >> Optional.of(type)
        1 * operation.memberRequests >> []
        0 * _

        expect:
        context.single().toString() == 'x'
    }
}
