package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Conversion;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * The local (wall-time, no instant) temporal family's single-hop spoke conversion to and from the
 * {@code LocalDateTime} hub (design D1/D2 of change {@code add-temporal-type-mapping}): {@code LocalDate}. Neither
 * hop reads a zone — the local family never crosses a zone boundary on its own. {@code LocalDate → LocalDateTime}
 * uses {@code atStartOfDay()} (the one place {@code 00:00:00} legitimately appears: the source inherently never had
 * a time — D2's no-truncation invariant). {@code LocalDateTime → LocalDate} uses {@code toLocalDate()}, an explicit
 * user-requested narrowing to a date-only target, not a hub silently dropping time. ({@code java.sql.Date} is
 * deliberately outside the auto roster — see the {@code temporal-conversion} spec.)
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class LocalTemporalConversion extends Conversion {

    private static final String LOCAL_DATE = "java.time.LocalDate";
    private static final String LOCAL_DATE_TIME = "java.time.LocalDateTime";

    @Override
    protected Stream<Step> conversions(final TypeMirror target, final ResolveCtx ctx) {
        if (ctx.isType(target, LOCAL_DATE_TIME)) {
            return atStartOfDayStep(ctx).stream();
        }
        if (ctx.isType(target, LOCAL_DATE)) {
            return toLocalDateStep(ctx).stream();
        }
        return Stream.empty();
    }

    /** {@code LocalDate → LocalDateTime} via {@code atStartOfDay()} — the source inherently carries no time. */
    static Optional<Step> atStartOfDayStep(final ResolveCtx ctx) {
        final var localDateElement = ctx.typeElementNamed(LOCAL_DATE);
        final var localDateTimeElement = ctx.typeElementNamed(LOCAL_DATE_TIME);
        if (localDateElement == null || localDateTimeElement == null) {
            return Optional.empty();
        }
        final var localDateType = localDateElement.asType();
        final var localDateTimeType = localDateTimeElement.asType();
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L.atStartOfDay()", inputs.single());
        return Optional.of(
                new Step(localDateType, Labels.conversion(localDateType, localDateTimeType), Weights.STEP, codegen));
    }

    /** {@code LocalDateTime → LocalDate} via {@code toLocalDate()} — a user-requested narrowing, not a hub truncation. */
    static Optional<Step> toLocalDateStep(final ResolveCtx ctx) {
        final var localDateElement = ctx.typeElementNamed(LOCAL_DATE);
        final var localDateTimeElement = ctx.typeElementNamed(LOCAL_DATE_TIME);
        if (localDateElement == null || localDateTimeElement == null) {
            return Optional.empty();
        }
        final var localDateType = localDateElement.asType();
        final var localDateTimeType = localDateTimeElement.asType();
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L.toLocalDate()", inputs.single());
        return Optional.of(new Step(
                localDateTimeType, Labels.conversion(localDateTimeType, localDateType), Weights.STEP, codegen));
    }
}
