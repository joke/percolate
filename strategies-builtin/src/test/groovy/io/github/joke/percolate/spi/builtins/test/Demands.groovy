package io.github.joke.percolate.spi.builtins.test

import io.github.joke.percolate.spi.Candidate
import io.github.joke.percolate.spi.Demand
import io.github.joke.percolate.spi.Directive
import io.github.joke.percolate.spi.Nullability

import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror

/**
 * Builds the myopic {@link Demand} contexts the driver would hand a strategy, for unit specs that exercise a
 * single strategy in isolation. Replaces the former {@code Frontiers} fixture (the {@code Frontier} surface is
 * gone): a demand carries the demanded type and nullness, the in-effect {@code @Map} {@link Directive}, the
 * declared-children goal-spec set, the in-scope candidate snapshot, and the nullness oracle a strategy threads
 * its produced port/output types through.
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
        demand(parentType, Nullability.NON_NULL, [new Candidate(parentType)],
                directive([segment], null, null), [] as Set, oracleNullness)
    }

    /** A bare demand asking for {@code target}, no directive, with the given candidate types in view. */
    static Demand forTarget(final TypeMirror target, final List<TypeMirror> candidateTypes = [],
                            final Nullability targetNullness = Nullability.NON_NULL) {
        demand(target, targetNullness, candidateTypes.collect { new Candidate(it) }, null, [] as Set,
                Nullability.NON_NULL)
    }

    /** An assembly demand asking for {@code target} whose declared-children goal spec is {@code declaredChildren}. */
    static Demand assembling(final TypeMirror target, final Set<String> declaredChildren) {
        demand(target, Nullability.NON_NULL, [], null, declaredChildren, Nullability.NON_NULL)
    }

    /** A demand asking for {@code target} whose directive declares a present {@code constant}. */
    static Demand withConstant(final TypeMirror target, final String constant) {
        demand(target, Nullability.NON_NULL, [], directive([], constant, null), [] as Set, Nullability.NON_NULL)
    }

    /**
     * A demand asking for {@code target} whose directive declares a present {@code defaultValue}, with the given
     * candidate types in view (the source value being defaulted).
     */
    static Demand withDefault(final TypeMirror target, final String defaultValue,
                              final List<TypeMirror> candidateTypes) {
        demand(target, Nullability.NON_NULL, candidateTypes.collect { new Candidate(it) },
                directive([], null, defaultValue), [] as Set, Nullability.NON_NULL)
    }

    private static Demand demand(final TypeMirror target, final Nullability targetNullness,
                                 final List<Candidate> candidates, final Directive directive,
                                 final Set<String> declaredChildren, final Nullability oracleNullness) {
        new Demand() {
            TypeMirror targetType() { target }

            Nullability targetNullness() { targetNullness }

            Optional<Directive> directive() { Optional.ofNullable(directive) }

            Set<String> declaredChildren() { declaredChildren }

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
