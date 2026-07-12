package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.javapoet.CodeBlock
import io.github.joke.percolate.spi.IncomingValues
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link AbsoluteTemporalConversion} unit-tested mock-only over the {@link ResolveCtx} type-query seam: the four
 * absolute-family spoke↔{@code Instant} hops are driven entirely by stubbed seam questions over opaque
 * {@link TypeMirror} tokens. No javac.
 */
@Tag('unit')
class AbsoluteTemporalConversionSpec extends Specification {

    ResolveCtx ctx = Mock()

    TypeMirror instantType = Mock()
    TypeMirror dateType = Mock()
    TypeMirror timestampType = Mock()
    TypeMirror offsetDateTimeType = Mock()
    TypeMirror zonedDateTimeType = Mock()

    static IncomingValues singleInput(final CodeBlock value) {
        [single: { -> value }] as IncomingValues
    }

    def setup() {
        element('java.time.Instant', instantType)
        element('java.util.Date', dateType)
        element('java.sql.Timestamp', timestampType)
        element('java.time.OffsetDateTime', offsetDateTimeType)
        element('java.time.ZonedDateTime', zonedDateTimeType)
    }

    def 'demanding Instant offers a toInstant() step from every absolute spoke'() {
        ctx.isType(instantType, 'java.time.Instant') >> true

        when:
        def specs = new AbsoluteTemporalConversion().expand(Demands.forTarget(instantType), ctx).toList()

        then:
        specs.size() == 4
        specs.every { it.ports.size() == 1 && it.outputType.is(instantType) && it.weight == Weights.STEP }
        specs*.ports*.get(0)*.type as Set == [dateType, timestampType, offsetDateTimeType, zonedDateTimeType] as Set

        and: 'the rendered step reads "toInstant()" off the spoke, e.g. Date -> Instant'
        specs.find { it.ports[0].type.is(dateType) }.codegen.render(singleInput(CodeBlock.of('d'))).toString() ==
                'd.toInstant()'
    }

    def 'demanding Date offers a Date.from(Instant) step, no zone'() {
        ctx.isType(dateType, 'java.util.Date') >> true

        when:
        def specs = new AbsoluteTemporalConversion().expand(Demands.forTarget(dateType), ctx).toList()

        then: 'the codegen renders Target.from($L) — not stringified here since $T needs a real compiler type'
        specs.size() == 1
        specs[0].ports.size() == 1
        specs[0].ports[0].type.is(instantType)
        specs[0].outputType.is(dateType)
        specs[0].weight == Weights.STEP
        specs[0].consumedOptionKeys.empty
    }

    def 'demanding Timestamp offers a Timestamp.from(Instant) step'() {
        ctx.isType(timestampType, 'java.sql.Timestamp') >> true

        when:
        def specs = new AbsoluteTemporalConversion().expand(Demands.forTarget(timestampType), ctx).toList()

        then:
        specs.size() == 1
        specs[0].ports[0].type.is(instantType)
        specs[0].outputType.is(timestampType)
    }

    def 'demanding OffsetDateTime offers an atOffset(ZoneOffset.UTC) step — fixed, no configured zone read'() {
        ctx.isType(offsetDateTimeType, 'java.time.OffsetDateTime') >> true

        when:
        def specs = new AbsoluteTemporalConversion().expand(Demands.forTarget(offsetDateTimeType), ctx).toList()

        then:
        specs.size() == 1
        specs[0].ports[0].type.is(instantType)
        specs[0].outputType.is(offsetDateTimeType)
        def rendered = specs[0].codegen.render(singleInput(CodeBlock.of('i'))).toString()
        rendered.contains('.atOffset(')
        rendered.contains('UTC')

        and: 'a spoke conversion is directive-blind and stamps no option'
        specs[0].consumedOptionKeys.empty
    }

    def 'demanding ZonedDateTime offers an atZone(ZoneOffset.UTC) step — fixed, no configured zone read'() {
        ctx.isType(zonedDateTimeType, 'java.time.ZonedDateTime') >> true

        when:
        def specs = new AbsoluteTemporalConversion().expand(Demands.forTarget(zonedDateTimeType), ctx).toList()

        then:
        specs.size() == 1
        specs[0].ports[0].type.is(instantType)
        specs[0].outputType.is(zonedDateTimeType)
        def rendered = specs[0].codegen.render(singleInput(CodeBlock.of('i'))).toString()
        rendered.contains('.atZone(')
        rendered.contains('UTC')
    }

    def 'a partial temporal type (e.g. LocalTime) is not matched at all'() {
        TypeMirror localTimeType = Mock()

        expect:
        new AbsoluteTemporalConversion().expand(Demands.forTarget(localTimeType), ctx).toList().empty
    }

    private void element(final String fqn, final TypeMirror type) {
        TypeElement typeElement = Mock()
        typeElement.asType() >> type
        ctx.typeElementNamed(fqn) >> typeElement
    }
}
