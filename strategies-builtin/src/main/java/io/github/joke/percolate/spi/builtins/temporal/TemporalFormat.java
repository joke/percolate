package io.github.joke.percolate.spi.builtins.temporal;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.ClassName;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.DirectiveInput;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.MemberRequest;
import io.github.joke.percolate.spi.Offer;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.builtins.Labels;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

import static io.github.joke.percolate.spi.Nullability.NON_NULL;
import static io.github.joke.percolate.spi.Weights.STEP;

// @Map(format = "…") for String ⇄ java.time types (design D6 of change add-temporal-type-mapping): parses a
// String source into, and renders a String from, LocalDate, LocalDateTime, OffsetDateTime, or ZonedDateTime,
// via a java.time.format.DateTimeFormatter — immutable and thread-safe, so it is requested as a single shared
// private static final class member (deduplicated by pattern) rather than rebuilt per call. Implements
// ExpansionStrategy directly (like InstantLocalDateTimeBridge) because it reads the directive and stamps
// "format" consumed. Formatting is target-blind in one direction (any roster java.time type in scope may format
// to String, over-emitted so the engine picks whichever is reachable) and source-blind in the other (String
// always parses to the demanded java.time target).
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
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var formatInput = demand.directive().flatMap(directive -> directive.input(FORMAT_KEY));
        final var pattern = formatInput.flatMap(DirectiveInput::getValue);
        if (pattern.isEmpty()) {
            return Stream.empty();
        }
        final var target = demand.targetType();
        final var memberRequest = formatterRequest(pattern.get());
        final var input = formatInput.orElseThrow();
        if (ctx.isType(target, STRING)) {
            return JAVA_TIME_ROSTER.stream()
                    .map(fqn -> formatStep(fqn, target, memberRequest, input, ctx))
                    .flatMap(Optional::stream)
                    .map(Offer::of);
        }
        return parseStep(target, memberRequest, input, ctx)
                .map(Offer::of)
                .map(Stream::of)
                .orElseGet(Stream::empty);
    }

    MemberRequest formatterRequest(final String pattern) {
        return new MemberRequest(
                DATE_TIME_FORMATTER,
                CodeBlock.of("$T.ofPattern($S)", DATE_TIME_FORMATTER, pattern),
                DEDUP_PREFIX + pattern);
    }

    // sourceFqn.format(formatter) — one over-emitted candidate per roster java.time source type.
    Optional<OperationSpec> formatStep(
            final String sourceFqn,
            final TypeMirror target,
            final MemberRequest memberRequest,
            final DirectiveInput formatInput,
            final ResolveCtx ctx) {
        final var sourceElement = ctx.typeElementNamed(sourceFqn);
        if (sourceElement == null) {
            return Optional.empty();
        }
        final var sourceType = sourceElement.asType();
        final OperationCodegen codegen =
                inputs -> CodeBlock.of("$L.format($L)", inputs.single(), inputs.member(memberRequest.getDedupKey()));
        final var port = new Port(VALUE_ROLE, sourceType, NON_NULL);
        return Optional.of(
                OperationSpec.of(Labels.conversion(sourceType, target), codegen, STEP, List.of(port), target, NON_NULL)
                        .withConsumed(Set.of(formatInput))
                        .withMemberRequests(List.of(memberRequest)));
    }

    // Target.parse(str, formatter) — the demanded java.time target, parsed from a String.
    Optional<OperationSpec> parseStep(
            final TypeMirror target,
            final MemberRequest memberRequest,
            final DirectiveInput formatInput,
            final ResolveCtx ctx) {
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
        final var port = new Port(VALUE_ROLE, stringType, NON_NULL);
        return Optional.of(OperationSpec.ofPartial(
                        Labels.conversion(stringType, target), codegen, STEP, List.of(port), target, NON_NULL)
                .withConsumed(Set.of(formatInput))
                .withMemberRequests(List.of(memberRequest)));
    }
}
