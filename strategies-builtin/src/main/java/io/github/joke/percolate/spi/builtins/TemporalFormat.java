package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.javapoet.ClassName;
import io.github.joke.percolate.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.MemberRequest;
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
 * {@code @Map(format = "…")} for {@code String ⇄ java.time} types (design D6 of change
 * {@code add-temporal-type-mapping}): parses a {@code String} source into, and renders a {@code String} from,
 * {@code LocalDate}, {@code LocalDateTime}, {@code OffsetDateTime}, or {@code ZonedDateTime}, via a
 * {@link java.time.format.DateTimeFormatter} — immutable and thread-safe, so it is requested as a single shared
 * {@code private static final} class member (deduplicated by pattern) rather than rebuilt per call. Implements
 * {@link ExpansionStrategy} directly (like {@link InstantLocalDateTimeBridge}) because it reads the directive and
 * stamps {@code "format"} consumed. Formatting is target-blind in one direction (any roster {@code java.time} type
 * in scope may format to {@code String}, over-emitted so the engine picks whichever is reachable) and
 * source-blind in the other ({@code String} always parses to the demanded {@code java.time} target).
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class TemporalFormat implements ExpansionStrategy {

    private static final ClassName DATE_TIME_FORMATTER = ClassName.get("java.time.format", "DateTimeFormatter");
    private static final String STRING = "java.lang.String";
    private static final List<String> JAVA_TIME_ROSTER = List.of(
            "java.time.LocalDate", "java.time.LocalDateTime", "java.time.OffsetDateTime", "java.time.ZonedDateTime");
    private static final String VALUE_ROLE = "value";
    private static final String FORMAT_KEY = "format";
    private static final String DEDUP_PREFIX = "temporal-format:";

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var pattern = demand.directive().flatMap(Directive::format);
        if (pattern.isEmpty()) {
            return Stream.empty();
        }
        final var target = demand.targetType();
        final var memberRequest = formatterRequest(pattern.get());
        if (ctx.isType(target, STRING)) {
            return JAVA_TIME_ROSTER.stream()
                    .map(fqn -> formatStep(fqn, target, memberRequest, ctx))
                    .flatMap(Optional::stream);
        }
        return parseStep(target, memberRequest, ctx).map(Stream::of).orElseGet(Stream::empty);
    }

    static MemberRequest formatterRequest(final String pattern) {
        return new MemberRequest(
                DATE_TIME_FORMATTER,
                CodeBlock.of("$T.ofPattern($S)", DATE_TIME_FORMATTER, pattern),
                DEDUP_PREFIX + pattern);
    }

    /** {@code sourceFqn.format(formatter)} — one over-emitted candidate per roster {@code java.time} source type. */
    static Optional<OperationSpec> formatStep(
            final String sourceFqn, final TypeMirror target, final MemberRequest memberRequest, final ResolveCtx ctx) {
        final var sourceElement = ctx.typeElementNamed(sourceFqn);
        if (sourceElement == null) {
            return Optional.empty();
        }
        final var sourceType = sourceElement.asType();
        final OperationCodegen codegen =
                inputs -> CodeBlock.of("$L.format($L)", inputs.single(), inputs.member(memberRequest.getDedupKey()));
        final var port = new Port(VALUE_ROLE, sourceType, Nullability.NON_NULL);
        return Optional.of(OperationSpec.of(
                        Labels.conversion(sourceType, target),
                        codegen,
                        Weights.STEP,
                        List.of(port),
                        target,
                        Nullability.NON_NULL)
                .withConsumedOptionKeys(Set.of(FORMAT_KEY))
                .withMemberRequests(List.of(memberRequest)));
    }

    /** {@code Target.parse(str, formatter)} — the demanded {@code java.time} target, parsed from a {@code String}. */
    static Optional<OperationSpec> parseStep(
            final TypeMirror target, final MemberRequest memberRequest, final ResolveCtx ctx) {
        final var isRosterTarget = JAVA_TIME_ROSTER.stream().anyMatch(fqn -> ctx.isType(target, fqn));
        if (!isRosterTarget) {
            return Optional.empty();
        }
        final var stringElement = ctx.typeElementNamed(STRING);
        if (stringElement == null) {
            return Optional.empty();
        }
        final var stringType = stringElement.asType();
        final OperationCodegen codegen = inputs ->
                CodeBlock.of("$T.parse($L, $L)", target, inputs.single(), inputs.member(memberRequest.getDedupKey()));
        final var port = new Port(VALUE_ROLE, stringType, Nullability.NON_NULL);
        return Optional.of(OperationSpec.ofPartial(
                        Labels.conversion(stringType, target),
                        codegen,
                        Weights.STEP,
                        List.of(port),
                        target,
                        Nullability.NON_NULL)
                .withConsumedOptionKeys(Set.of(FORMAT_KEY))
                .withMemberRequests(List.of(memberRequest)));
    }
}
