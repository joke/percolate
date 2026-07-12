package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.javapoet.CodeBlock
import io.github.joke.percolate.spi.IncomingValues
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link LegacyTemporalFormat} unit-tested mock-only: {@code @Map(format = …)} for {@code java.util.Date}/
 * {@code java.sql.Timestamp}, always a fresh per-call {@code SimpleDateFormat} — never a shared member (it is not
 * thread-safe).
 */
@Tag('unit')
class LegacyTemporalFormatSpec extends Specification {

    ResolveCtx ctx = Mock()

    TypeMirror stringType = Mock()
    TypeMirror dateType = Mock()
    TypeMirror timestampType = Mock()

    static IncomingValues singleInput(final CodeBlock value) {
        [single: { -> value }] as IncomingValues
    }

    def setup() {
        element('java.lang.String', stringType)
        element('java.util.Date', dateType)
        element('java.sql.Timestamp', timestampType)
    }

    def 'formatting a Date to String uses a fresh, per-call SimpleDateFormat — no member requested'() {
        ctx.isType(dateType, 'java.lang.String') >> true

        when:
        def specs = new LegacyTemporalFormat().expand(Demands.withFormat(dateType, 'yyyy-MM-dd'), ctx).toList()

        then:
        specs.size() == 2
        specs.every { it.memberRequests.empty }
        specs.every { it.consumedOptionKeys == ['format'] as Set }
        specs.every { !it.partial }

        and: 'the first candidate renders a fresh SimpleDateFormat().format(...) inline, not a shared field'
        def rendered = specs[0].codegen.render(singleInput(CodeBlock.of('d'))).toString()
        rendered.contains('new')
        rendered.contains('SimpleDateFormat("yyyy-MM-dd")')
        rendered.contains('.format(d)')
    }

    def 'parsing String into Date wraps the checked ParseException, no member requested'() {
        ctx.isType(dateType, 'java.util.Date') >> true

        when:
        def specs = new LegacyTemporalFormat().expand(Demands.withFormat(dateType, 'yyyy-MM-dd'), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.partial
        spec.memberRequests.empty
        spec.consumedOptionKeys == ['format'] as Set
        spec.ports[0].type.is(stringType)
        spec.outputType.is(dateType)
        def rendered = spec.codegen.render(singleInput(CodeBlock.of('s'))).toString()
        rendered.contains('SimpleDateFormat("yyyy-MM-dd")')
        rendered.contains('.parse(s)')
        rendered.contains('ParseException')
        rendered.contains('RuntimeException')
    }

    def 'parsing String into Timestamp wraps the parsed Date in a new Timestamp'() {
        ctx.isType(timestampType, 'java.util.Date') >> false
        ctx.isType(timestampType, 'java.sql.Timestamp') >> true

        when:
        def specs = new LegacyTemporalFormat().expand(Demands.withFormat(timestampType, 'yyyy-MM-dd'), ctx).toList()

        then:
        specs.size() == 1
        specs[0].outputType.is(timestampType)
        def rendered = specs[0].codegen.render(singleInput(CodeBlock.of('s'))).toString()
        rendered.contains('new java.sql.Timestamp(')
        rendered.contains('.getTime())')
    }

    def 'a demand with no format directive is not matched'() {
        expect:
        new LegacyTemporalFormat().expand(Demands.forTarget(dateType), ctx).toList().empty
    }

    def '@Map(format) on a non-legacy-temporal, non-String target is not matched (nothing to consume it)'() {
        TypeMirror intType = Mock()

        expect:
        new LegacyTemporalFormat().expand(Demands.withFormat(intType, 'yyyy-MM-dd'), ctx).toList().empty
    }

    private void element(final String fqn, final TypeMirror type) {
        TypeElement typeElement = Mock()
        typeElement.asType() >> type
        ctx.typeElementNamed(fqn) >> typeElement
    }
}
