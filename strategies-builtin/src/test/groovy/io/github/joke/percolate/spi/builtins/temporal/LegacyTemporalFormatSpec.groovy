package io.github.joke.percolate.spi.builtins.temporal

import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.spi.DirectiveInput
import io.github.joke.percolate.spi.IncomingValues
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Subjects
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.Labels
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * {@link LegacyTemporalFormat} unit-tested mock-only: {@code @Map(format = …)} for {@code java.util.Date}/
 * {@code java.sql.Timestamp}, always a fresh per-call {@code SimpleDateFormat} — never a shared member (it is not
 * thread-safe).
 */
@Tag('unit')
class LegacyTemporalFormatSpec extends Specification {

    ResolveCtx ctx = Mock()
    LegacyTemporalFormat legacyTemporalFormat = new LegacyTemporalFormat()

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
        TypeElement dateElement = Mock()
        TypeElement timestampElement = Mock()

        when:
        def specs = legacyTemporalFormat.expand(Demands.withFormat(dateType, 'yyyy-MM-dd'), ctx)*.spec

        then: 'exactly the two legacy source types are resolved — no third, unnamed source is attempted'
        1 * ctx.isType(dateType, 'java.lang.String') >> true
        1 * ctx.typeElementNamed('java.util.Date') >> dateElement
        1 * dateElement.asType() >> dateType
        1 * ctx.typeElementNamed('java.sql.Timestamp') >> timestampElement
        1 * timestampElement.asType() >> timestampType
        3 * dateType.kind >> TypeKind.ERROR
        1 * timestampType.kind >> TypeKind.ERROR
        0 * _

        expect:
        specs.size() == 2
        specs.every { it.memberRequests.empty }
        specs.every { it.consumed*.key == ['format'] }
        specs.every { !it.partial }

        and: 'the first candidate renders a fresh SimpleDateFormat().format(...) inline, not a shared field'
        specs.every { it.weight == Weights.STEP }
        specs.every { it.outputType.is(dateType) && it.outputNullness == Nullability.NON_NULL }
        specs*.ports*.get(0)*.type == [dateType, timestampType]
        specs[0].label == "${dateType}${Labels.ARROW}${dateType}"
        specs[1].label == "${timestampType}${Labels.ARROW}${dateType}"
        specs[0].codegen.render(singleInput(CodeBlock.of('d'))).toString()
                == 'new java.text.SimpleDateFormat("yyyy-MM-dd").format(d)'
        specs[1].codegen.render(singleInput(CodeBlock.of('t'))).toString()
                == 'new java.text.SimpleDateFormat("yyyy-MM-dd").format(t)'
    }

    def 'parsing String into Date wraps the checked ParseException, no member requested'() {
        ctx.isType(dateType, 'java.util.Date') >> true

        when:
        def specs = legacyTemporalFormat.expand(Demands.withFormat(dateType, 'yyyy-MM-dd'), ctx)*.spec

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.partial
        spec.memberRequests.empty
        spec.consumed*.key == ['format']
        spec.ports[0].type.is(stringType)
        spec.outputType.is(dateType)
        spec.weight == Weights.STEP
        spec.outputNullness == Nullability.NON_NULL
        spec.label == "${stringType}${Labels.ARROW}${dateType}"
        spec.codegen.render(singleInput(CodeBlock.of('s'))).toString() == parseAsDateExpr('yyyy-MM-dd', 's')
    }

    def 'parsing String into Timestamp wraps the parsed Date in a new Timestamp'() {
        ctx.isType(timestampType, 'java.util.Date') >> false
        ctx.isType(timestampType, 'java.sql.Timestamp') >> true

        when:
        def specs = legacyTemporalFormat.expand(Demands.withFormat(timestampType, 'yyyy-MM-dd'), ctx)*.spec

        then:
        specs.size() == 1
        specs[0].outputType.is(timestampType)
        specs[0].ports[0].type.is(stringType)
        specs[0].partial
        specs[0].codegen.render(singleInput(CodeBlock.of('s'))).toString()
                == "new java.sql.Timestamp(${parseAsDateExpr('yyyy-MM-dd', 's')}.getTime())"
    }

    def 'a demand with no format directive is not matched'() {
        expect:
        legacyTemporalFormat.expand(Demands.forTarget(dateType), ctx).toList().empty
    }

    def '@Map(format) on a non-legacy-temporal, non-String target is not matched (nothing to consume it)'() {
        TypeMirror intType = Mock()

        expect:
        legacyTemporalFormat.expand(Demands.withFormat(intType, 'yyyy-MM-dd'), ctx).toList().empty
    }

    def 'formatStep returns empty when the legacy source type is not resolvable'() {
        ResolveCtx freshCtx = Mock()

        expect:
        legacyTemporalFormat.formatStep('java.util.Date', stringType, 'p', formatInput(), freshCtx).empty
    }

    def 'legacyTargetKind is false for Date, true for Timestamp, empty otherwise'() {
        TypeMirror otherType = Mock()
        ctx.isType(dateType, 'java.util.Date') >> true
        ctx.isType(timestampType, 'java.util.Date') >> false
        ctx.isType(timestampType, 'java.sql.Timestamp') >> true
        ctx.isType(otherType, 'java.util.Date') >> false
        ctx.isType(otherType, 'java.sql.Timestamp') >> false

        expect:
        legacyTemporalFormat.legacyTargetKind(dateType, ctx) == Optional.of(false)
        legacyTemporalFormat.legacyTargetKind(timestampType, ctx) == Optional.of(true)
        legacyTemporalFormat.legacyTargetKind(otherType, ctx).empty
    }

    def 'parseStep returns empty when the target is neither Date nor Timestamp'() {
        TypeMirror otherType = Mock()
        ctx.isType(otherType, 'java.util.Date') >> false
        ctx.isType(otherType, 'java.sql.Timestamp') >> false

        expect:
        legacyTemporalFormat.parseStep(otherType, 'p', formatInput(), ctx).empty
    }

    def 'parseStep returns empty when String itself is not resolvable'() {
        ResolveCtx freshCtx = Mock()
        freshCtx.isType(dateType, 'java.util.Date') >> true

        expect:
        legacyTemporalFormat.parseStep(dateType, 'p', formatInput(), freshCtx).empty
    }

    def 'dateParseCodegen renders a fresh SimpleDateFormat parse, wrapping the checked exception'() {
        expect:
        legacyTemporalFormat.dateParseCodegen('yyyy-MM-dd').render(singleInput(CodeBlock.of('s'))).toString()
                == parseAsDateExpr('yyyy-MM-dd', 's')
    }

    def 'timestampParseCodegen wraps a parsed Date in a new Timestamp'() {
        expect:
        legacyTemporalFormat.timestampParseCodegen('yyyy-MM-dd').render(singleInput(CodeBlock.of('s'))).toString()
                == "new java.sql.Timestamp(${parseAsDateExpr('yyyy-MM-dd', 's')}.getTime())"
    }

    def 'parseAsDate builds the checked-exception-wrapping supplier both parse codegens share'() {
        expect:
        legacyTemporalFormat.parseAsDate('dd.MM.yyyy', CodeBlock.of('raw')).toString()
                == parseAsDateExpr('dd.MM.yyyy', 'raw')
    }

    private static String parseAsDateExpr(final String pattern, final String source) {
        '((java.util.function.Supplier<java.util.Date>) () -> { try { return new java.text.SimpleDateFormat' +
                "(\"${pattern}\").parse(${source}); } catch (java.text.ParseException e) " +
                '{ throw new java.lang.RuntimeException(e); } }).get()'
    }

    private static DirectiveInput formatInput() {
        DirectiveInput.scalar('format', 'p', Subjects.none())
    }

    private void element(final String fqn, final TypeMirror type) {
        TypeElement typeElement = Mock()
        typeElement.asType() >> type
        ctx.typeElementNamed(fqn) >> typeElement
    }
}
