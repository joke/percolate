package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.spi.IncomingValues
import io.github.joke.percolate.spi.ResolveCtx
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
        def specs = new InstantLocalDateTimeBridge()
                .expand(Demands.withZone(localDateTimeType, 'Europe/Berlin'), ctx)
                .toList()

        then:
        specs.size() == 1
        specs[0].ports[0].type.is(instantType)
        specs[0].outputType.is(localDateTimeType)
        specs[0].consumedOptionKeys == ['zone'] as Set
        def rendered = specs[0].codegen.render(singleInput(CodeBlock.of('i'))).toString()
        rendered.contains('atZone(')
        rendered.contains('"Europe/Berlin"')
        rendered.contains('toLocalDateTime()')
    }

    def 'absent directive zone falls back to the configured processor option, frozen, not stamped'() {
        ctx.isType(localDateTimeType, 'java.time.LocalDateTime') >> true
        ctx.configuredTimeZone() >> Optional.of('UTC')

        when:
        def specs = new InstantLocalDateTimeBridge().expand(Demands.forTarget(localDateTimeType), ctx).toList()

        then:
        specs.size() == 1
        specs[0].consumedOptionKeys.empty
        def rendered = specs[0].codegen.render(singleInput(CodeBlock.of('i'))).toString()
        rendered.contains('"UTC"')
    }

    def 'absent directive zone and absent processor option defers to generated systemDefault()'() {
        ctx.isType(localDateTimeType, 'java.time.LocalDateTime') >> true
        ctx.configuredTimeZone() >> Optional.empty()

        when:
        def specs = new InstantLocalDateTimeBridge().expand(Demands.forTarget(localDateTimeType), ctx).toList()

        then:
        specs.size() == 1
        specs[0].consumedOptionKeys.empty
        def rendered = specs[0].codegen.render(singleInput(CodeBlock.of('i'))).toString()
        rendered.contains('systemDefault()')
        !rendered.contains('"')
    }

    def 'demanding Instant from LocalDateTime crosses the bridge the other way'() {
        ctx.isType(instantType, 'java.time.LocalDateTime') >> false
        ctx.isType(instantType, 'java.time.Instant') >> true
        ctx.configuredTimeZone() >> Optional.empty()

        when:
        def specs = new InstantLocalDateTimeBridge().expand(Demands.forTarget(instantType), ctx).toList()

        then:
        specs.size() == 1
        specs[0].ports[0].type.is(localDateTimeType)
        specs[0].outputType.is(instantType)
        def rendered = specs[0].codegen.render(singleInput(CodeBlock.of('dt'))).toString()
        rendered.contains('.atZone(')
        rendered.contains('.toInstant()')
    }

    def 'a non-bridging target is not matched'() {
        TypeMirror stringType = Mock()

        expect:
        new InstantLocalDateTimeBridge().expand(Demands.forTarget(stringType), ctx).toList().empty
    }

    def 'toLocalDateTimeSpec returns empty when Instant is not resolvable'() {
        ResolveCtx freshCtx = Mock()
        TypeElement localDateTimeElement = Mock()
        freshCtx.typeElementNamed('java.time.LocalDateTime') >> localDateTimeElement

        expect:
        InstantLocalDateTimeBridge.toLocalDateTimeSpec(Demands.forTarget(localDateTimeType), localDateTimeType, freshCtx).empty
    }

    def 'toLocalDateTimeSpec returns empty when LocalDateTime is not resolvable'() {
        ResolveCtx freshCtx = Mock()
        TypeElement instantElement = Mock()
        freshCtx.typeElementNamed('java.time.Instant') >> instantElement

        expect:
        InstantLocalDateTimeBridge.toLocalDateTimeSpec(Demands.forTarget(localDateTimeType), localDateTimeType, freshCtx).empty
    }

    def 'toInstantSpec returns empty when Instant is not resolvable'() {
        ResolveCtx freshCtx = Mock()
        TypeElement localDateTimeElement = Mock()
        freshCtx.typeElementNamed('java.time.LocalDateTime') >> localDateTimeElement

        expect:
        InstantLocalDateTimeBridge.toInstantSpec(Demands.forTarget(instantType), instantType, freshCtx).empty
    }

    def 'toInstantSpec returns empty when LocalDateTime is not resolvable'() {
        ResolveCtx freshCtx = Mock()
        TypeElement instantElement = Mock()
        freshCtx.typeElementNamed('java.time.Instant') >> instantElement

        expect:
        InstantLocalDateTimeBridge.toInstantSpec(Demands.forTarget(instantType), instantType, freshCtx).empty
    }

    def 'consumedKeys is the zone key when a directive zone is present, else empty'() {
        expect:
        InstantLocalDateTimeBridge.consumedKeys(Optional.of('UTC')) == ['zone'] as Set
        InstantLocalDateTimeBridge.consumedKeys(Optional.empty()) == [] as Set
    }

    def 'resolveZone prefers a present directive zone, frozen as ZoneId.of'() {
        expect:
        InstantLocalDateTimeBridge.resolveZone(Optional.of('Europe/Berlin'), ctx).toString() == 'java.time.ZoneId.of("Europe/Berlin")'
    }

    def 'resolveZone falls back to the configured processor option when no directive zone is present'() {
        ctx.configuredTimeZone() >> Optional.of('UTC')

        expect:
        InstantLocalDateTimeBridge.resolveZone(Optional.empty(), ctx).toString() == 'java.time.ZoneId.of("UTC")'
    }

    def 'resolveZone defers to generated systemDefault() when neither directive nor configured zone is present'() {
        ctx.configuredTimeZone() >> Optional.empty()

        expect:
        InstantLocalDateTimeBridge.resolveZone(Optional.empty(), ctx).toString() == 'java.time.ZoneId.systemDefault()'
    }

    private static IncomingValues singleInput(final CodeBlock value) {
        [single: { -> value }] as IncomingValues
    }
}
