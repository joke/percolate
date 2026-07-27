package io.github.joke.percolate.spi.builtins.enumconversion;

import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.BodyCodegen;
import io.github.joke.percolate.spi.BodyRenderContext;
import io.github.joke.percolate.spi.DirectiveInput;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.Offer;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.PortType;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Subjects;
import io.github.joke.percolate.spi.SwitchStyle;
import io.github.joke.percolate.spi.Weights;
import io.github.joke.percolate.spi.builtins.Labels;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * Enum-to-enum conversion via a declared conversion method (design of change {@code add-enum-conversion-mapping}):
 * fires whenever the demanded target is an {@code enum}, declaring a bare top-level type-variable port that
 * {@code Grounding} unifies against the in-scope source — the mechanism {@link TemporalFormat} relies on for its
 * roster, generalised to an unbounded source. The concrete source is learned only at render time, through the
 * {@link BodyRenderContext} (design D3): {@link #render} enumerates the grounded source enum's constants,
 * same-name-matches each against the target enum, applies {@code @MapEnum} overrides with precedence, and renders
 * either a classic switch statement or a modern arrow switch expression depending on the effective
 * {@link SwitchStyle} (design D4/D6) — the engine makes none of this decision.
 *
 * <p>Weighted at {@link Weights#EXPENSIVE}, well above a method call ({@link Weights#METHOD}): when a user's own
 * declared conversion method could also satisfy the same demand (reached through {@link MethodCallBridge}), that
 * cheaper, explicit path wins over this inline fallback (design Risk/Trade-off: "competition with a user's
 * hand-written conversion method" — resolved by cost, never by special-casing the engine).
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class EnumConversion implements ExpansionStrategy {

    private static final String VALUE_ROLE = "value";
    static final String ENUM_KEY = "enum";
    private static final String SOURCE_PART = "source";
    private static final String TARGET_PART = "target";

    // SourceVersion.RELEASE_14 cannot be referenced as a compile-time symbol under this module's --release 11
    // target (it postdates the JDK 11 platform API `--release` restricts compilation to); the toolchain JDK the
    // processor actually runs on always has it, so a runtime valueOf lookup resolves it safely.
    private static final SourceVersion JAVA_14 = SourceVersion.valueOf("RELEASE_14");

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var target = demand.targetType();
        if (!ctx.isEnum(target)) {
            return Stream.empty();
        }
        final var overrides = demand.directive().map(d -> d.inputs(ENUM_KEY)).orElseGet(List::of);
        final var port = new Port(
                VALUE_ROLE, target, Nullability.NON_NULL, PortType.variable(0, sourceBound(target, overrides)));
        final BodyCodegen codegen = context -> render(context, target, overrides);
        return Stream.of(Offer.of(OperationSpec.of(
                        "enum" + Labels.ARROW + Labels.simple(target),
                        codegen,
                        Weights.EXPENSIVE,
                        List.of(port),
                        target,
                        Nullability.NON_NULL)
                .withConsumed(effectiveOverrides(target, overrides, ctx))));
    }

    /**
     * The overrides that name a real target constant — the rail (design D3 of change
     * {@code decouple-engine-from-strategy-semantics}) reports any other {@code @MapEnum} entry as declared but
     * having had no effect, replacing {@code ValidateEnumOverridesStage}'s target-side check.
     */
    static Set<DirectiveInput> effectiveOverrides(
            final TypeMirror target, final List<DirectiveInput> overrides, final ResolveCtx ctx) {
        if (overrides.isEmpty()) {
            return Set.of();
        }
        final var targetConstants = Set.copyOf(enumConstantNames(ctx, target));
        return overrides.stream()
                .filter(override ->
                        targetConstants.contains(override.member(TARGET_PART).orElseThrow()))
                .collect(toUnmodifiableSet());
    }

    /**
     * A {@link PortType.Bound} rejecting a non-enum source or one whose constants are not all covered by a
     * same-name match or {@code @MapEnum} (design D6 of change {@code decouple-engine-from-strategy-semantics}):
     * the grounding is vetoed before it ever competes, so {@link #render} never sees either failure.
     */
    static PortType.Bound sourceBound(final TypeMirror target, final List<DirectiveInput> overrides) {
        return (source, ctx) -> {
            if (!ctx.isEnum(source)) {
                return Optional.of(
                        Offer.refusal(Subjects.none(), "enum conversion requires an enum source, found " + source));
            }
            final var sourceConstants = enumConstantNames(ctx, source);
            final var mapping = buildMapping(sourceConstants, enumConstantNames(ctx, target), overrides);
            final var uncovered = sourceConstants.stream()
                    .filter(constant -> !mapping.containsKey(constant))
                    .collect(toUnmodifiableList());
            return uncovered.isEmpty()
                    ? Optional.empty()
                    : Optional.of(Offer.refusal(
                            Subjects.none(),
                            "no @MapEnum or same-name match covers source constant(s): "
                                    + String.join(", ", uncovered)));
        };
    }

    /** Renders the whole method body: a switch over the grounded source enum, form chosen by the effective style. */
    static CodeBlock render(
            final BodyRenderContext context, final TypeMirror target, final List<DirectiveInput> overrides) {
        final var resolveCtx = context.resolveCtx();
        final var source = context.portType(VALUE_ROLE);
        final var sourceConstants = enumConstantNames(resolveCtx, source);
        final var mapping = buildMapping(sourceConstants, enumConstantNames(resolveCtx, target), overrides);
        final var style = resolveStyle(context.switchStyle(), context.sourceVersion());
        return style == SwitchStyle.ARROW
                ? renderArrow(context.single(), target, sourceConstants, mapping)
                : renderClassic(context.single(), target, sourceConstants, mapping);
    }

    /** {@code AUTO} resolves against the target {@link SourceVersion}: arrow for Java 14+, else classic. */
    static SwitchStyle resolveStyle(final SwitchStyle configured, final SourceVersion sourceVersion) {
        if (configured != SwitchStyle.AUTO) {
            return configured;
        }
        return sourceVersion.compareTo(JAVA_14) >= 0 ? SwitchStyle.ARROW : SwitchStyle.CLASSIC;
    }

    /** Same-name matches first, then {@code @MapEnum} overrides — which take precedence over a coincidental match. */
    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded render; insertion order matters
    static Map<String, String> buildMapping(
            final List<String> sourceConstants,
            final List<String> targetConstants,
            final List<DirectiveInput> overrides) {
        final var targetSet = Set.copyOf(targetConstants);
        final Map<String, String> mapping = new LinkedHashMap<>();
        for (final var constant : sourceConstants) {
            if (targetSet.contains(constant)) {
                mapping.put(constant, constant);
            }
        }
        overrides.forEach(override -> mapping.put(
                override.member(SOURCE_PART).orElseThrow(),
                override.member(TARGET_PART).orElseThrow()));
        return mapping;
    }

    /** A modern switch expression with no {@code default}: javac's own exhaustiveness check rejects a gap. */
    static CodeBlock renderArrow(
            final CodeBlock sourceExpr,
            final TypeMirror target,
            final List<String> sourceConstants,
            final Map<String, String> mapping) {
        final var builder =
                CodeBlock.builder().add("return switch ($L) {\n", sourceExpr).indent();
        sourceConstants.stream()
                .filter(mapping::containsKey)
                .forEach(constant -> builder.addStatement("case $L -> $T.$L", constant, target, mapping.get(constant)));
        return builder.unindent().add("};\n").build();
    }

    /** A classic switch statement: {@link #sourceBound} guarantees every source constant is already covered. */
    static CodeBlock renderClassic(
            final CodeBlock sourceExpr,
            final TypeMirror target,
            final List<String> sourceConstants,
            final Map<String, String> mapping) {
        final var builder =
                CodeBlock.builder().add("switch ($L) {\n", sourceExpr).indent();
        for (final var constant : sourceConstants) {
            builder.add("case $L:\n", constant)
                    .indent()
                    .addStatement("return $T.$L", target, mapping.get(constant))
                    .unindent();
        }
        builder.add("default:\n")
                .indent()
                .addStatement("throw new $T($S)", IllegalStateException.class, "Unexpected enum constant")
                .unindent();
        return builder.unindent().add("}\n").build();
    }

    /** {@code type}'s declared enum constants, in declaration order; empty when {@code type} has no backing element. */
    static List<String> enumConstantNames(final ResolveCtx ctx, final TypeMirror type) {
        return ctx.asTypeElement(type)
                .map(element -> ctx.membersOf(element)
                        .filter(member -> member.getKind() == ElementKind.ENUM_CONSTANT)
                        .map(member -> member.getSimpleName().toString())
                        .collect(toUnmodifiableList()))
                .orElseGet(List::of);
    }
}
