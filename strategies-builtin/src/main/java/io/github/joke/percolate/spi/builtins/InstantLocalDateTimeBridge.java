package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * The single zone-consuming hop between the two temporal hubs (design D1/D3/D4 of change
 * {@code add-temporal-type-mapping}): {@code Instant ⇄ LocalDateTime}. Unlike every spoke conversion, this strategy
 * implements {@link ExpansionStrategy} directly because it reads the demand's {@link Directive} for the resolved
 * zone and stamps {@code "zone"} consumed on the {@link OperationSpec} it emits — the consumption-tracked option
 * rail's contract (a strategy stamps only the keys it actually read). Zone resolution follows a fixed precedence
 * (D4): a present {@code @Map(zone = …)} wins, frozen as {@code ZoneId.of("…")}; else a present
 * {@code -Apercolate.time.zone=…} processor option, also frozen; else the generated code reads
 * {@code ZoneId.systemDefault()} at the <em>consumer's</em> runtime. The processor never reads its own build-JVM
 * zone. A zone declared on a binding whose winning plan never crosses this bridge (an absolute-only or local-only
 * path) is therefore never stamped, and the directive-options rail reports it as having no effect.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class InstantLocalDateTimeBridge implements ExpansionStrategy {

    private static final String INSTANT = "java.time.Instant";
    private static final String LOCAL_DATE_TIME = "java.time.LocalDateTime";
    private static final ClassName ZONE_ID = ClassName.get("java.time", "ZoneId");
    private static final String VALUE_ROLE = "value";
    private static final String ZONE_KEY = "zone";

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var target = demand.targetType();
        if (ctx.isType(target, LOCAL_DATE_TIME)) {
            return toLocalDateTimeSpec(demand, target, ctx).stream();
        }
        if (ctx.isType(target, INSTANT)) {
            return toInstantSpec(demand, target, ctx).stream();
        }
        return Stream.empty();
    }

    /** {@code Instant -> LocalDateTime} via {@code instant.atZone(zone).toLocalDateTime()}. */
    static Optional<OperationSpec> toLocalDateTimeSpec(
            final ProduceDemand demand, final TypeMirror target, final ResolveCtx ctx) {
        final var instantElement = ctx.typeElementNamed(INSTANT);
        final var localDateTimeElement = ctx.typeElementNamed(LOCAL_DATE_TIME);
        if (instantElement == null || localDateTimeElement == null) {
            return Optional.empty();
        }
        final var instantType = instantElement.asType();
        final var localDateTimeType = localDateTimeElement.asType();
        final var directiveZone = demand.directive().flatMap(Directive::zone);
        final var zoneExpr = resolveZone(directiveZone, ctx);
        final OperationCodegen codegen =
                inputs -> CodeBlock.of("$L.atZone($L).toLocalDateTime()", inputs.single(), zoneExpr);
        final var port = new Port(VALUE_ROLE, instantType, Nullability.NON_NULL);
        return Optional.of(OperationSpec.of(
                        Labels.conversion(instantType, localDateTimeType),
                        codegen,
                        Weights.STEP,
                        List.of(port),
                        target,
                        Nullability.NON_NULL)
                .withConsumedOptionKeys(consumedKeys(directiveZone)));
    }

    /** {@code LocalDateTime -> Instant} via {@code localDateTime.atZone(zone).toInstant()}. */
    static Optional<OperationSpec> toInstantSpec(
            final ProduceDemand demand, final TypeMirror target, final ResolveCtx ctx) {
        final var instantElement = ctx.typeElementNamed(INSTANT);
        final var localDateTimeElement = ctx.typeElementNamed(LOCAL_DATE_TIME);
        if (instantElement == null || localDateTimeElement == null) {
            return Optional.empty();
        }
        final var instantType = instantElement.asType();
        final var localDateTimeType = localDateTimeElement.asType();
        final var directiveZone = demand.directive().flatMap(Directive::zone);
        final var zoneExpr = resolveZone(directiveZone, ctx);
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L.atZone($L).toInstant()", inputs.single(), zoneExpr);
        final var port = new Port(VALUE_ROLE, localDateTimeType, Nullability.NON_NULL);
        return Optional.of(OperationSpec.of(
                        Labels.conversion(localDateTimeType, instantType),
                        codegen,
                        Weights.STEP,
                        List.of(port),
                        target,
                        Nullability.NON_NULL)
                .withConsumedOptionKeys(consumedKeys(directiveZone)));
    }

    static Set<String> consumedKeys(final Optional<String> directiveZone) {
        return directiveZone.isPresent() ? Set.of(ZONE_KEY) : Set.of();
    }

    /** Zone precedence (D4): directive → processor option → generated {@code ZoneId.systemDefault()}. */
    static CodeBlock resolveZone(final Optional<String> directiveZone, final ResolveCtx ctx) {
        if (directiveZone.isPresent()) {
            return CodeBlock.of("$T.of($S)", ZONE_ID, directiveZone.get());
        }
        final var configured = ctx.configuredTimeZone();
        if (configured.isPresent()) {
            return CodeBlock.of("$T.of($S)", ZONE_ID, configured.get());
        }
        return CodeBlock.of("$T.systemDefault()", ZONE_ID);
    }
}
