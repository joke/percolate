package io.github.joke.percolate.spi.builtins.temporal

import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.spi.DirectiveInput
import io.github.joke.percolate.spi.IncomingValues
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Subjects
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.Labels
import io.github.joke.percolate.spi.builtins.test.Demands
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link InstantLocalDateTimeBridge} unit-tested mock-only: the single zone-consuming hop between the two temporal
 * hubs, driven from {@code ProduceDemand}/{@code ResolveCtx} only (myopic — no graph access). Zone resolution
 * precedence (directive → processor option → generated {@code systemDefault()}) and consumption stamping are
 * covered by example-based cases.
 */
@Tag('unit')
class InstantLocalDateTimeBridgeSpec extends Specification {

    ResolveCtx ctx = Mock()
    InstantLocalDateTimeBridge instantLocalDateTimeBridge = new InstantLocalDateTimeBridge()

    TypeMirror instantType = Mock()
    TypeMirror localDateTimeType = Mock()
    TypeMirror zoneIdType = Mock()

    def setup() {
        TypeElement instantElement = Mock()
        instantElement.asType() >> instantType
        ctx.typeElementNamed('java.time.Instant') >> instantElement

        TypeElement localDateTimeElement = Mock()
        localDateTimeElement.asType() >> localDateTimeType
        ctx.typeElementNamed('java.time.LocalDateTime') >> localDateTimeElement

        TypeElement zoneIdElement = Mock()
        zoneIdElement.asType() >> zoneIdType
        ctx.typeElementNamed('java.time.ZoneId') >> zoneIdElement
    }

    def 'a present @Map(zone) wins, is frozen, and is stamped consumed'() {
        ctx.isType(localDateTimeType, 'java.time.LocalDateTime') >> true

        when:
        def specs = instantLocalDateTimeBridge
                .expand(Demands.withZone(localDateTimeType, 'Europe/Berlin'), ctx)
                *.spec

        then:
        specs.size() == 1
        specs[0].ports[0].type.is(instantType)
        specs[0].outputType.is(localDateTimeType)
        specs[0].consumed*.key == ['zone']
        specs[0].weight == Weights.STEP
        specs[0].label == "${instantType}${Labels.ARROW}${localDateTimeType}"
        specs[0].codegen.render(singleInput(CodeBlock.of('i'))).toString()
                == 'i.atZone(java.time.ZoneId.of("Europe/Berlin")).toLocalDateTime()'
    }

    def 'absent directive zone falls back to the configured processor option, frozen, not stamped'() {
        ctx.isType(localDateTimeType, 'java.time.LocalDateTime') >> true
        ctx.option('percolate.time.zone') >> Optional.of('UTC')

        when:
        def specs = instantLocalDateTimeBridge.expand(Demands.forTarget(localDateTimeType), ctx)*.spec

        then:
        specs.size() == 1
        specs[0].consumed.empty
        specs[0].codegen.render(singleInput(CodeBlock.of('i'))).toString()
                == 'i.atZone(java.time.ZoneId.of("UTC")).toLocalDateTime()'
    }

    def 'absent directive zone and absent processor option defers to generated systemDefault()'() {
        ctx.isType(localDateTimeType, 'java.time.LocalDateTime') >> true
        ctx.option('percolate.time.zone') >> Optional.empty()

        when:
        def specs = instantLocalDateTimeBridge.expand(Demands.forTarget(localDateTimeType), ctx)*.spec

        then:
        specs.size() == 1
        specs[0].consumed.empty
        specs[0].codegen.render(singleInput(CodeBlock.of('i'))).toString()
                == 'i.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()'
    }

    def 'demanding Instant from LocalDateTime crosses the bridge the other way'() {
        ctx.isType(instantType, 'java.time.LocalDateTime') >> false
        ctx.isType(instantType, 'java.time.Instant') >> true
        ctx.option('percolate.time.zone') >> Optional.empty()

        when:
        def specs = instantLocalDateTimeBridge.expand(Demands.forTarget(instantType), ctx)*.spec

        then:
        specs.size() == 1
        specs[0].ports[0].type.is(localDateTimeType)
        specs[0].outputType.is(instantType)
        specs[0].weight == Weights.STEP
        specs[0].label == "${localDateTimeType}${Labels.ARROW}${instantType}"
        specs[0].consumed.empty
        specs[0].codegen.render(singleInput(CodeBlock.of('dt'))).toString()
                == 'dt.atZone(java.time.ZoneId.systemDefault()).toInstant()'
    }

    // The reverse crossing reads the same @Map(zone) member, freezes it, and stamps it consumed.
    def 'a present @Map(zone) also drives, and is consumed by, the LocalDateTime to Instant crossing'() {
        ctx.isType(instantType, 'java.time.LocalDateTime') >> false
        ctx.isType(instantType, 'java.time.Instant') >> true

        when:
        def specs = instantLocalDateTimeBridge.expand(Demands.withZone(instantType, 'Europe/Berlin'), ctx)*.spec

        then:
        specs.size() == 1
        specs[0].consumed*.key == ['zone']
        specs[0].codegen.render(singleInput(CodeBlock.of('dt'))).toString()
                == 'dt.atZone(java.time.ZoneId.of("Europe/Berlin")).toInstant()'
    }

    def 'a non-bridging target is not matched'() {
        TypeMirror stringType = Mock()

        expect:
        instantLocalDateTimeBridge.expand(Demands.forTarget(stringType), ctx).toList().empty
    }

    def 'toLocalDateTimeSpec returns empty when Instant is not resolvable'() {
        ResolveCtx freshCtx = Mock()
        TypeElement localDateTimeElement = Mock()
        freshCtx.typeElementNamed('java.time.LocalDateTime') >> localDateTimeElement

        expect:
        instantLocalDateTimeBridge.toLocalDateTimeSpec(Demands.forTarget(localDateTimeType), localDateTimeType, freshCtx).empty
    }

    def 'toLocalDateTimeSpec returns empty when LocalDateTime is not resolvable'() {
        ResolveCtx freshCtx = Mock()
        TypeElement instantElement = Mock()
        freshCtx.typeElementNamed('java.time.Instant') >> instantElement

        expect:
        instantLocalDateTimeBridge.toLocalDateTimeSpec(Demands.forTarget(localDateTimeType), localDateTimeType, freshCtx).empty
    }

    def 'toInstantSpec returns empty when Instant is not resolvable'() {
        ResolveCtx freshCtx = Mock()
        TypeElement localDateTimeElement = Mock()
        freshCtx.typeElementNamed('java.time.LocalDateTime') >> localDateTimeElement

        expect:
        instantLocalDateTimeBridge.toInstantSpec(Demands.forTarget(instantType), instantType, freshCtx).empty
    }

    def 'toInstantSpec returns empty when LocalDateTime is not resolvable'() {
        ResolveCtx freshCtx = Mock()
        TypeElement instantElement = Mock()
        freshCtx.typeElementNamed('java.time.Instant') >> instantElement

        expect:
        instantLocalDateTimeBridge.toInstantSpec(Demands.forTarget(instantType), instantType, freshCtx).empty
    }

    def 'consumed is the zone input when a directive zone is present, else empty'() {
        expect:
        instantLocalDateTimeBridge.consumed(Optional.of(zoneInput('UTC'))) == [zoneInput('UTC')] as Set
        instantLocalDateTimeBridge.consumed(Optional.empty()) == [] as Set
    }

    def 'resolveZone prefers a present directive zone, frozen as ZoneId.of'() {
        expect:
        instantLocalDateTimeBridge.resolveZone(Optional.of(zoneInput('Europe/Berlin')), ctx).toString() ==
                'java.time.ZoneId.of("Europe/Berlin")'
    }

    def 'resolveZone falls back to the configured processor option when no directive zone is present'() {
        ctx.option('percolate.time.zone') >> Optional.of('UTC')

        expect:
        instantLocalDateTimeBridge.resolveZone(Optional.empty(), ctx).toString() == 'java.time.ZoneId.of("UTC")'
    }

    def 'resolveZone defers to generated systemDefault() when neither directive nor configured zone is present'() {
        ctx.option('percolate.time.zone') >> Optional.empty()

        expect:
        instantLocalDateTimeBridge.resolveZone(Optional.empty(), ctx).toString() == 'java.time.ZoneId.systemDefault()'
    }

    private static DirectiveInput zoneInput(final String value) {
        DirectiveInput.scalar('zone', value, Subjects.none())
    }

    private static IncomingValues singleInput(final CodeBlock value) {
        [single: { -> value }] as IncomingValues
    }
}
