package io.github.joke.percolate.spi.builtins.temporal;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.ClassName;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Conversion;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.builtins.Labels;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.spi.Weights.STEP;
import static java.util.stream.Stream.concat;

// The absolute (instant-based) temporal family's single-hop spoke conversions to and from the Instant hub
// (design D1 of change add-temporal-type-mapping): java.util.Date, java.sql.Timestamp, OffsetDateTime, and
// ZonedDateTime. Every hop is target-driven and directive-blind — none reads a zone — because a spoke↔hub hop
// within one family never needs one (D1): OffsetDateTime/ZonedDateTime fix their zone/offset at the literal UTC
// in either direction, which never truncates the instant (the point in time is identical; only its zone-free
// textual representation changes) and never depends on the build machine's zone. The engine composes any cross-
// spoke pair (e.g. OffsetDateTime → Date) through Instant for free; a cross-family pair additionally crosses
// the zone bridge (InstantLocalDateTimeBridge).
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class AbsoluteTemporalConversion extends Conversion {

    private static final String INSTANT = "java.time.Instant";
    private static final String DATE = "java.util.Date";
    private static final String TIMESTAMP = "java.sql.Timestamp";
    private static final String OFFSET_DATE_TIME = "java.time.OffsetDateTime";
    private static final String ZONED_DATE_TIME = "java.time.ZonedDateTime";
    private static final ClassName ZONE_OFFSET = ClassName.get("java.time", "ZoneOffset");

    private static final List<String> METHOD_SPOKES = List.of(DATE, TIMESTAMP);
    private static final List<String> FIXED_OFFSET_SPOKES = List.of(OFFSET_DATE_TIME, ZONED_DATE_TIME);

    @Override
    @VisibleForTesting
    protected Stream<Step> conversions(final TypeMirror target, final ResolveCtx ctx) {
        if (ctx.isType(target, INSTANT)) {
            return toInstantSteps(ctx);
        }
        return fromInstantStep(target, ctx).stream();
    }

    Stream<Step> toInstantSteps(final ResolveCtx ctx) {
        return concat(
                METHOD_SPOKES.stream().map(fqn -> toInstantByMethod(fqn, ctx)).flatMap(Optional::stream),
                FIXED_OFFSET_SPOKES.stream()
                        .map(fqn -> toInstantByMethod(fqn, ctx))
                        .flatMap(Optional::stream));
    }

    // spokeFqn's no-arg toInstant() accessor: $L.toInstant(), input type spokeFqn.
    Optional<Step> toInstantByMethod(final String spokeFqn, final ResolveCtx ctx) {
        final var spokeElement = ctx.typeElementNamed(spokeFqn);
        final var instantElement = ctx.typeElementNamed(INSTANT);
        if (spokeElement == null || instantElement == null) {
            return Optional.empty();
        }
        final var spokeType = spokeElement.asType();
        final var instantType = instantElement.asType();
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L.toInstant()", inputs.single());
        return Optional.of(new Step(spokeType, Labels.conversion(spokeType, instantType), STEP, codegen));
    }

    Optional<Step> fromInstantStep(final TypeMirror target, final ResolveCtx ctx) {
        if (isMethodSpoke(target, ctx)) {
            return fromInstantByFactory(target, ctx);
        }
        return fixedOffsetMethodName(target, ctx).flatMap(method -> fromInstantAtFixedOffset(target, ctx, method));
    }

    boolean isMethodSpoke(final TypeMirror target, final ResolveCtx ctx) {
        return METHOD_SPOKES.stream().anyMatch(fqn -> ctx.isType(target, fqn));
    }

    // The Instant accessor method name for a fixed-offset spoke target, or empty when target is neither.
    Optional<String> fixedOffsetMethodName(final TypeMirror target, final ResolveCtx ctx) {
        if (ctx.isType(target, OFFSET_DATE_TIME)) {
            return Optional.of("atOffset");
        }
        if (ctx.isType(target, ZONED_DATE_TIME)) {
            return Optional.of("atZone");
        }
        return Optional.empty();
    }

    // target's static from(Instant) factory: Target.from($L).
    Optional<Step> fromInstantByFactory(final TypeMirror target, final ResolveCtx ctx) {
        final var instantElement = ctx.typeElementNamed(INSTANT);
        if (instantElement == null) {
            return Optional.empty();
        }
        final var instantType = instantElement.asType();
        final OperationCodegen codegen = inputs -> CodeBlock.of("$T.from($L)", target, inputs.single());
        return Optional.of(new Step(instantType, Labels.conversion(instantType, target), STEP, codegen));
    }

    // Instant.atOffset/.atZone fixed at the literal ZoneOffset.UTC — zone-free, no truncation.
    Optional<Step> fromInstantAtFixedOffset(final TypeMirror target, final ResolveCtx ctx, final String method) {
        final var instantElement = ctx.typeElementNamed(INSTANT);
        if (instantElement == null) {
            return Optional.empty();
        }
        final var instantType = instantElement.asType();
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L.$N($T.UTC)", inputs.single(), method, ZONE_OFFSET);
        return Optional.of(new Step(instantType, Labels.conversion(instantType, target), STEP, codegen));
    }
}
