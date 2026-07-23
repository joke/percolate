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
 * {@link LocalTemporalConversion} unit-tested mock-only over the {@link ResolveCtx} type-query seam: the local
 * family's {@code LocalDate ⇄ LocalDateTime} hop is driven entirely by stubbed seam questions. No javac.
 */
@Tag('unit')
class LocalTemporalConversionSpec extends Specification {

    ResolveCtx ctx = Mock()

    TypeMirror localDateType = Mock()
    TypeMirror localDateTimeType = Mock()

    def setup() {
        TypeElement localDateElement = Mock()
        localDateElement.asType() >> localDateType
        ctx.typeElementNamed('java.time.LocalDate') >> localDateElement

        TypeElement localDateTimeElement = Mock()
        localDateTimeElement.asType() >> localDateTimeType
        ctx.typeElementNamed('java.time.LocalDateTime') >> localDateTimeElement
    }

    def 'demanding LocalDateTime from LocalDate uses atStartOfDay() — the source inherently has no time'() {
        ctx.isType(localDateTimeType, 'java.time.LocalDateTime') >> true

        when:
        def specs = new LocalTemporalConversion().expand(Demands.forTarget(localDateTimeType), ctx).toList()

        then:
        specs.size() == 1
        specs[0].ports.size() == 1
        specs[0].ports[0].type.is(localDateType)
        specs[0].outputType.is(localDateTimeType)
        specs[0].weight == Weights.STEP
        specs[0].codegen.render(singleInput(CodeBlock.of('d'))).toString() == 'd.atStartOfDay()'
    }

    def 'demanding LocalDate from LocalDateTime uses toLocalDate() — a requested narrowing, not a hub truncation'() {
        ctx.isType(localDateType, 'java.time.LocalDate') >> true

        when:
        def specs = new LocalTemporalConversion().expand(Demands.forTarget(localDateType), ctx).toList()

        then:
        specs.size() == 1
        specs[0].ports[0].type.is(localDateTimeType)
        specs[0].outputType.is(localDateType)
        specs[0].codegen.render(singleInput(CodeBlock.of('dt'))).toString() == 'dt.toLocalDate()'
    }

    def 'a non-local temporal target is not matched'() {
        TypeMirror instantType = Mock()

        expect:
        new LocalTemporalConversion().expand(Demands.forTarget(instantType), ctx).toList().empty
    }

    def 'atStartOfDayStep returns empty when LocalDate is not resolvable'() {
        ResolveCtx freshCtx = Mock()
        TypeElement localDateTimeElement = Mock()
        freshCtx.typeElementNamed('java.time.LocalDateTime') >> localDateTimeElement

        expect:
        LocalTemporalConversion.atStartOfDayStep(freshCtx).empty
    }

    def 'atStartOfDayStep returns empty when LocalDateTime is not resolvable'() {
        ResolveCtx freshCtx = Mock()
        TypeElement localDateElement = Mock()
        freshCtx.typeElementNamed('java.time.LocalDate') >> localDateElement

        expect:
        LocalTemporalConversion.atStartOfDayStep(freshCtx).empty
    }

    def 'toLocalDateStep returns empty when LocalDate is not resolvable'() {
        ResolveCtx freshCtx = Mock()
        TypeElement localDateTimeElement = Mock()
        freshCtx.typeElementNamed('java.time.LocalDateTime') >> localDateTimeElement

        expect:
        LocalTemporalConversion.toLocalDateStep(freshCtx).empty
    }

    def 'toLocalDateStep returns empty when LocalDateTime is not resolvable'() {
        ResolveCtx freshCtx = Mock()
        TypeElement localDateElement = Mock()
        freshCtx.typeElementNamed('java.time.LocalDate') >> localDateElement

        expect:
        LocalTemporalConversion.toLocalDateStep(freshCtx).empty
    }

    private static IncomingValues singleInput(final CodeBlock value) {
        [single: { -> value }] as IncomingValues
    }
}
