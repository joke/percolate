package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.ClassName;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
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
 * {@code @Map(format = "…")} for {@code String ⇄ java.util.Date}/{@code java.sql.Timestamp} (design D6 of change
 * {@code add-temporal-type-mapping}): unlike {@link TemporalFormat}, this uses a <b>fresh, per-call</b>
 * {@code new java.text.SimpleDateFormat(pattern)} — it is <b>not</b> thread-safe, so it declares no member request
 * (never shared, never hoisted). {@code SimpleDateFormat#parse} declares the checked {@code ParseException}; since
 * a production is a single expression (no statement-level codegen), parsing is wrapped in an immediately-invoked
 * cast lambda that rethrows it unchecked — the standard idiom for a checked call in expression position.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class LegacyTemporalFormat implements ExpansionStrategy {

    private static final ClassName SIMPLE_DATE_FORMAT = ClassName.get("java.text", "SimpleDateFormat");
    private static final ClassName PARSE_EXCEPTION = ClassName.get("java.text", "ParseException");
    private static final ClassName DATE = ClassName.get("java.util", "Date");
    private static final ClassName TIMESTAMP = ClassName.get("java.sql", "Timestamp");
    private static final ClassName SUPPLIER = ClassName.get("java.util.function", "Supplier");
    private static final ClassName RUNTIME_EXCEPTION = ClassName.get(RuntimeException.class);
    private static final String STRING = "java.lang.String";
    private static final String VALUE_ROLE = "value";
    private static final String FORMAT_KEY = "format";

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var pattern = demand.directive().flatMap(Directive::format);
        if (pattern.isEmpty()) {
            return Stream.empty();
        }
        final var target = demand.targetType();
        if (ctx.isType(target, STRING)) {
            return Stream.of("java.util.Date", "java.sql.Timestamp")
                    .map(fqn -> formatStep(fqn, target, pattern.get(), ctx))
                    .flatMap(Optional::stream);
        }
        return parseStep(target, pattern.get(), ctx).map(Stream::of).orElseGet(Stream::empty);
    }

    /** {@code new SimpleDateFormat(pattern).format($L)} — a fresh formatter per call, never shared. */
    static Optional<OperationSpec> formatStep(
            final String sourceFqn, final TypeMirror target, final String pattern, final ResolveCtx ctx) {
        final var sourceElement = ctx.typeElementNamed(sourceFqn);
        if (sourceElement == null) {
            return Optional.empty();
        }
        final var sourceType = sourceElement.asType();
        final OperationCodegen codegen =
                inputs -> CodeBlock.of("new $T($S).format($L)", SIMPLE_DATE_FORMAT, pattern, inputs.single());
        final var port = new Port(VALUE_ROLE, sourceType, Nullability.NON_NULL);
        return Optional.of(OperationSpec.of(
                        Labels.conversion(sourceType, target),
                        codegen,
                        Weights.STEP,
                        List.of(port),
                        target,
                        Nullability.NON_NULL)
                .withConsumedOptionKeys(Set.of(FORMAT_KEY)));
    }

    /** {@code String -> Date/Timestamp} via a fresh {@code SimpleDateFormat}, its checked {@code ParseException} rethrown. */
    static Optional<OperationSpec> parseStep(final TypeMirror target, final String pattern, final ResolveCtx ctx) {
        final var isTimestamp = legacyTargetKind(target, ctx);
        if (isTimestamp.isEmpty()) {
            return Optional.empty();
        }
        final var stringElement = ctx.typeElementNamed(STRING);
        if (stringElement == null) {
            return Optional.empty();
        }
        final var stringType = stringElement.asType();
        final OperationCodegen codegen = isTimestamp.get() ? timestampParseCodegen(pattern) : dateParseCodegen(pattern);
        final var port = new Port(VALUE_ROLE, stringType, Nullability.NON_NULL);
        return Optional.of(OperationSpec.ofPartial(
                        Labels.conversion(stringType, target),
                        codegen,
                        Weights.STEP,
                        List.of(port),
                        target,
                        Nullability.NON_NULL)
                .withConsumedOptionKeys(Set.of(FORMAT_KEY)));
    }

    /** Empty when {@code target} is neither legacy type; else {@code true} for {@code Timestamp}, {@code false} for {@code Date}. */
    static Optional<Boolean> legacyTargetKind(final TypeMirror target, final ResolveCtx ctx) {
        if (ctx.isType(target, "java.util.Date")) {
            return Optional.of(false);
        }
        if (ctx.isType(target, "java.sql.Timestamp")) {
            return Optional.of(true);
        }
        return Optional.empty();
    }

    static OperationCodegen dateParseCodegen(final String pattern) {
        return inputs -> parseAsDate(pattern, inputs.single());
    }

    static OperationCodegen timestampParseCodegen(final String pattern) {
        return inputs -> CodeBlock.of("new $T($L.getTime())", TIMESTAMP, parseAsDate(pattern, inputs.single()));
    }

    static CodeBlock parseAsDate(final String pattern, final CodeBlock source) {
        return CodeBlock.of(
                "(($T<$T>) () -> { try { return new $T($S).parse($L); } catch ($T e) { throw new $T(e); } }).get()",
                SUPPLIER,
                DATE,
                SIMPLE_DATE_FORMAT,
                pattern,
                source,
                PARSE_EXCEPTION,
                RUNTIME_EXCEPTION);
    }
}
