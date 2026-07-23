package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.spi.IncomingValues
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link TemporalFormat} unit-tested mock-only: {@code @Map(format = …)} parsing/rendering across the
 * {@code java.time} roster, backed by a hoisted shared {@code DateTimeFormatter} member request.
 */
@Tag('unit')
class TemporalFormatSpec extends Specification {

    ResolveCtx ctx = Mock()

    TypeMirror stringType = Mock()
    TypeMirror localDateType = Mock()
    TypeMirror localDateTimeType = Mock()
    TypeMirror offsetDateTimeType = Mock()
    TypeMirror zonedDateTimeType = Mock()

    static IncomingValues memberInput(final CodeBlock single, final String dedupKey, final CodeBlock memberRef) {
        [single: { -> single }, member: { String key -> key == dedupKey ? memberRef : null }] as IncomingValues
    }

    def setup() {
        element('java.lang.String', stringType)
        element('java.time.LocalDate', localDateType)
        element('java.time.LocalDateTime', localDateTimeType)
        element('java.time.OffsetDateTime', offsetDateTimeType)
        element('java.time.ZonedDateTime', zonedDateTimeType)
    }

    def 'parsing String into LocalDate requests a hoisted formatter, stamps format consumed, and is partial'() {
        ctx.isType(localDateType, 'java.lang.String') >> false
        ctx.isType(localDateType, 'java.time.LocalDate') >> true

        when:
        def specs = new TemporalFormat().expand(Demands.withFormat(localDateType, 'yyyy-MM-dd'), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.partial
        spec.ports.size() == 1
        spec.ports[0].type.is(stringType)
        spec.outputType.is(localDateType)
        spec.weight == Weights.STEP
        spec.consumedOptionKeys == ['format'] as Set
        spec.memberRequests.size() == 1
        spec.memberRequests[0].fieldType.toString() == 'java.time.format.DateTimeFormatter'
        spec.memberRequests[0].initializer.toString().contains('DateTimeFormatter.ofPattern("yyyy-MM-dd")')

        and: 'the codegen resolves the member reference by dedup key (the $T target itself is not stringified here — it needs a real compiler type)'
        def dedupKey = spec.memberRequests[0].dedupKey
        spec.codegen.render(memberInput(CodeBlock.of('s'), dedupKey, CodeBlock.of('FMT'))) != null
    }

    def 'formatting a java.time value to String over-emits one candidate per roster type, all sharing the formatter dedup key'() {
        ctx.isType(stringType, 'java.lang.String') >> true

        when:
        def specs = new TemporalFormat().expand(Demands.withFormat(stringType, 'yyyy-MM-dd'), ctx).toList()

        then:
        specs.size() == 4
        specs*.ports*.get(0)*.type as Set ==
                [localDateType, localDateTimeType, offsetDateTimeType, zonedDateTimeType] as Set
        specs.every { !it.partial }
        specs*.memberRequests*.get(0)*.dedupKey.unique().size() == 1

        and: 'the LocalDate candidate renders type.format(formatter)'
        def localDateSpec = specs.find { it.ports[0].type.is(localDateType) }
        def dedupKey = localDateSpec.memberRequests[0].dedupKey
        def rendered = localDateSpec.codegen.render(memberInput(CodeBlock.of('d'), dedupKey, CodeBlock.of('FMT'))).toString()
        rendered == 'd.format(FMT)'
    }

    def 'two demands with the same pattern request formatters under the same dedup key'() {
        ctx.isType(localDateType, 'java.time.LocalDate') >> true

        when:
        def first = new TemporalFormat().expand(Demands.withFormat(localDateType, 'yyyy-MM-dd'), ctx).toList()
        def second = new TemporalFormat().expand(Demands.withFormat(localDateType, 'yyyy-MM-dd'), ctx).toList()

        then:
        first[0].memberRequests[0].dedupKey == second[0].memberRequests[0].dedupKey
    }

    def 'two demands with distinct patterns request formatters under distinct dedup keys'() {
        ctx.isType(localDateType, 'java.time.LocalDate') >> true

        when:
        def first = new TemporalFormat().expand(Demands.withFormat(localDateType, 'yyyy-MM-dd'), ctx).toList()
        def second = new TemporalFormat().expand(Demands.withFormat(localDateType, 'dd.MM.yyyy'), ctx).toList()

        then:
        first[0].memberRequests[0].dedupKey != second[0].memberRequests[0].dedupKey
    }

    def 'a demand with no format directive is not matched'() {
        expect:
        new TemporalFormat().expand(Demands.forTarget(localDateType), ctx).toList().empty
    }

    def '@Map(format) on a non-temporal, non-String target is not matched (nothing to consume it)'() {
        TypeMirror intType = Mock()

        expect:
        new TemporalFormat().expand(Demands.withFormat(intType, 'yyyy-MM-dd'), ctx).toList().empty
    }

    def 'formatterRequest builds a DateTimeFormatter member request keyed by the pattern'() {
        expect:
        def request = TemporalFormat.formatterRequest('yyyy-MM-dd')
        request.fieldType.toString() == 'java.time.format.DateTimeFormatter'
        request.initializer.toString() == 'java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")'
        request.dedupKey == 'temporal-format:yyyy-MM-dd'
    }

    def 'formatStep returns empty when the roster source type is not resolvable'() {
        ResolveCtx freshCtx = Mock()

        expect:
        TemporalFormat.formatStep('java.time.LocalDate', stringType, TemporalFormat.formatterRequest('p'), freshCtx).empty
    }

    def 'parseStep returns empty when the target is not a roster type'() {
        TypeMirror intType = Mock()

        expect:
        TemporalFormat.parseStep(intType, TemporalFormat.formatterRequest('p'), ctx).empty
    }

    def 'parseStep returns empty when String itself is not resolvable'() {
        ResolveCtx freshCtx = Mock()
        TypeElement localDateElement = Mock()
        localDateElement.asType() >> localDateType
        freshCtx.typeElementNamed('java.time.LocalDate') >> localDateElement
        freshCtx.isType(localDateType, 'java.time.LocalDate') >> true

        expect:
        TemporalFormat.parseStep(localDateType, TemporalFormat.formatterRequest('p'), freshCtx).empty
    }

    private void element(final String fqn, final TypeMirror type) {
        TypeElement typeElement = Mock()
        typeElement.asType() >> type
        ctx.typeElementNamed(fqn) >> typeElement
    }
}
