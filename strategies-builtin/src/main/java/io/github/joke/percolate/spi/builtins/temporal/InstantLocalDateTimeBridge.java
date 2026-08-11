package io.github.joke.percolate.spi.builtins.temporal;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.ClassName;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.DirectiveInput;
import io.github.joke.percolate.spi.ExpansionStrategy;
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
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.spi.Nullability.NON_NULL;
import static io.github.joke.percolate.spi.Weights.STEP;

// The single zone-consuming hop between the two temporal hubs (design D1/D3/D4 of change add-temporal-type-
// mapping): Instant ⇄ LocalDateTime. Unlike every spoke conversion, this strategy implements ExpansionStrategy
// directly because it reads the demand's Directive for the resolved zone and stamps "zone" consumed on the
// OperationSpec it emits — the consumption-tracked option rail's contract (a strategy stamps only the keys it
// actually read). Zone resolution follows a fixed precedence (D4): a present @Map(zone = …) wins, frozen as
// ZoneId.of("…"); else a present -Apercolate.time.zone=… processor option, also frozen; else the generated code
// reads ZoneId.systemDefault() at the consumer's runtime. The processor never reads its own build-JVM zone. A
// zone declared on a binding whose winning plan never crosses this bridge (an absolute-only or local-only path)
// is therefore never stamped, and the directive-options rail reports it as having no effect.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class InstantLocalDateTimeBridge implements ExpansionStrategy {

    private static final String INSTANT = "java.time.Instant";
    private static final String LOCAL_DATE_TIME = "java.time.LocalDateTime";
    private static final ClassName ZONE_ID = ClassName.get("java.time", "ZoneId");
    private static final String VALUE_ROLE = "value";
    private static final String ZONE_KEY = "zone";

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var target = demand.targetType();
        if (ctx.isType(target, LOCAL_DATE_TIME)) {
            return toLocalDateTimeSpec(demand, target, ctx).map(Offer::of).stream();
        }
        if (ctx.isType(target, INSTANT)) {
            return toInstantSpec(demand, target, ctx).map(Offer::of).stream();
        }
        return Stream.empty();
    }

    // Instant -> LocalDateTime via instant.atZone(zone).toLocalDateTime().
    @VisibleForTesting
    Optional<OperationSpec> toLocalDateTimeSpec(
            final ProduceDemand demand, final TypeMirror target, final ResolveCtx ctx) {
        final var instantElement = ctx.typeElementNamed(INSTANT);
        final var localDateTimeElement = ctx.typeElementNamed(LOCAL_DATE_TIME);
        if (instantElement == null || localDateTimeElement == null) {
            return Optional.empty();
        }
        final var instantType = instantElement.asType();
        final var localDateTimeType = localDateTimeElement.asType();
        final var zoneInput = demand.directive().flatMap(directive -> directive.input(ZONE_KEY));
        final var zoneExpr = resolveZone(zoneInput, ctx);
        final OperationCodegen codegen =
                inputs -> CodeBlock.of("$L.atZone($L).toLocalDateTime()", inputs.single(), zoneExpr);
        final var port = new Port(VALUE_ROLE, instantType, NON_NULL);
        return Optional.of(OperationSpec.of(
                        Labels.conversion(instantType, localDateTimeType),
                        codegen,
                        STEP,
                        List.of(port),
                        target,
                        NON_NULL)
                .withConsumed(consumed(zoneInput)));
    }

    // LocalDateTime -> Instant via localDateTime.atZone(zone).toInstant().
    @VisibleForTesting
    Optional<OperationSpec> toInstantSpec(final ProduceDemand demand, final TypeMirror target, final ResolveCtx ctx) {
        final var instantElement = ctx.typeElementNamed(INSTANT);
        final var localDateTimeElement = ctx.typeElementNamed(LOCAL_DATE_TIME);
        if (instantElement == null || localDateTimeElement == null) {
            return Optional.empty();
        }
        final var instantType = instantElement.asType();
        final var localDateTimeType = localDateTimeElement.asType();
        final var zoneInput = demand.directive().flatMap(directive -> directive.input(ZONE_KEY));
        final var zoneExpr = resolveZone(zoneInput, ctx);
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L.atZone($L).toInstant()", inputs.single(), zoneExpr);
        final var port = new Port(VALUE_ROLE, localDateTimeType, NON_NULL);
        return Optional.of(OperationSpec.of(
                        Labels.conversion(localDateTimeType, instantType),
                        codegen,
                        STEP,
                        List.of(port),
                        target,
                        NON_NULL)
                .withConsumed(consumed(zoneInput)));
    }

    @VisibleForTesting
    Set<DirectiveInput> consumed(final Optional<DirectiveInput> zoneInput) {
        return zoneInput.map(Set::of).orElseGet(Set::of);
    }

    // Zone precedence (D4): directive → processor option → generated ZoneId.systemDefault().
    @VisibleForTesting
    CodeBlock resolveZone(final Optional<DirectiveInput> zoneInput, final ResolveCtx ctx) {
        final var directiveZone = zoneInput.flatMap(DirectiveInput::getValue);
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
