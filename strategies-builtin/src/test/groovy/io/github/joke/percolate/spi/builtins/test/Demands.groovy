package io.github.joke.percolate.spi.builtins.test

import io.github.joke.percolate.spi.Candidate
import io.github.joke.percolate.spi.Demand
import io.github.joke.percolate.spi.Directive
import io.github.joke.percolate.spi.Nullability

import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror

/**
 * Builds the myopic {@link Demand} contexts the driver would hand a strategy, for unit specs that exercise a
 * single strategy in isolation. A demand carries the demanded type and nullness, the in-effect {@code @Map}
 * {@link Directive}, the declared-children goal-spec set, the binding/slot name the demand serves, the in-scope
 * candidate snapshot (each candidate a {@code (type, nullness)} source value), and the nullness oracle a strategy
 * threads its produced port/output types through.
 */
final class Demands {

    private Demands() {
    }

    /**
     * A source-descent demand: the driver offers the parent value as the lone candidate and feeds the single
     * member segment to resolve via a one-segment directive. Mirrors how the driver drives a path-resolver
     * strategy. The {@code oracleNullness} is what the strategy's produced value is typed with.
     */
    static Demand descend(final TypeMirror parentType, final String segment,
                          final Nullability oracleNullness = Nullability.NON_NULL) {
        demand(parentType, Nullability.NON_NULL, [new Candidate(parentType, Nullability.NON_NULL)],
                directive([segment], null, null), [] as Set, segment, oracleNullness)
    }

    /** A bare demand asking for {@code target}, no directive, with the given candidate types (NON_NULL) in view. */
    static Demand forTarget(final TypeMirror target, final List<TypeMirror> candidateTypes = [],
                            final Nullability targetNullness = Nullability.NON_NULL) {
        demand(target, targetNullness, candidateTypes.collect { new Candidate(it, Nullability.NON_NULL) }, null,
                [] as Set, '', Nullability.NON_NULL)
    }

    /** An assembly demand asking for {@code target} whose declared-children goal spec is {@code declaredChildren}. */
    static Demand assembling(final TypeMirror target, final Set<String> declaredChildren) {
        demand(target, Nullability.NON_NULL, [], null, declaredChildren, '', Nullability.NON_NULL)
    }

    /** A demand asking for {@code target} whose directive declares a present {@code constant}. */
    static Demand withConstant(final TypeMirror target, final String constant) {
        demand(target, Nullability.NON_NULL, [], directive([], constant, null), [] as Set, '', Nullability.NON_NULL)
    }

    /**
     * A NON_NULL demand asking for {@code target} whose directive declares a present {@code defaultValue}, with the
     * given candidate types (NON_NULL) in view (the source value being defaulted).
     */
    static Demand withDefault(final TypeMirror target, final String defaultValue,
                              final List<TypeMirror> candidateTypes) {
        demand(target, Nullability.NON_NULL, candidateTypes.collect { new Candidate(it, Nullability.NON_NULL) },
                directive([], null, defaultValue), [] as Set, '', Nullability.NON_NULL)
    }

    /**
     * A NON_NULL crossing demand naming {@code slot}, with one candidate of {@code candidateType} at
     * {@code candidateNullness} and an optional {@code defaultValue} directive. Mirrors how the driver hands the
     * nullness-crossing strategy a {@code (nullable candidate, non-null demand)} pair.
     */
    static Demand crossing(final TypeMirror target, final TypeMirror candidateType,
                           final Nullability candidateNullness, final String slot,
                           final String defaultValue = null) {
        demand(target, Nullability.NON_NULL, [new Candidate(candidateType, candidateNullness)],
                directive([], null, defaultValue), [] as Set, slot, Nullability.NON_NULL)
    }

    private static Demand demand(final TypeMirror target, final Nullability targetNullness,
                                 final List<Candidate> candidates, final Directive directive,
                                 final Set<String> declaredChildren, final String bindingName,
                                 final Nullability oracleNullness) {
        new Demand() {
            TypeMirror targetType() { target }

            Nullability targetNullness() { targetNullness }

            Optional<Directive> directive() { Optional.ofNullable(directive) }

            Set<String> declaredChildren() { declaredChildren }

            String bindingName() { bindingName }

            List<Candidate> candidates() { candidates }

            @SuppressWarnings('UnusedMethodParameter')
            Nullability nullnessOf(final TypeMirror type, final Element scope) { oracleNullness }
        }
    }

    private static Directive directive(final List<String> sourcePath, final String constant, final String defaultValue) {
        new Directive() {
            List<String> sourcePath() { sourcePath }

            Optional<String> constant() { Optional.ofNullable(constant) }

            Optional<String> defaultValue() { Optional.ofNullable(defaultValue) }
        }
    }
}
