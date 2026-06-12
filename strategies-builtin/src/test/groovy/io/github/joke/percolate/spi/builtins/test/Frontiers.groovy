package io.github.joke.percolate.spi.builtins.test

import io.github.joke.percolate.spi.Candidate
import io.github.joke.percolate.spi.Directive
import io.github.joke.percolate.spi.Frontier

import javax.lang.model.type.TypeMirror

/**
 * Builds the myopic {@link Frontier} contexts the driver would hand a strategy, for unit specs that exercise a
 * single strategy in isolation.
 */
final class Frontiers {

    private Frontiers() {
    }

    /**
     * A source-descent frontier: the driver offers the parent value as the lone candidate and feeds the single
     * member segment to resolve via a one-segment directive. Mirrors how {@code SourceDescentExpander} drives a
     * path-resolver strategy.
     */
    static Frontier descend(final TypeMirror parentType, final String segment) {
        frontier(parentType, [new Candidate(parentType)], directive([segment], null, null))
    }

    /** A bare frontier asking for {@code target}, with no directive and the given candidate types in view. */
    static Frontier forTarget(final TypeMirror target, final List<TypeMirror> candidateTypes = []) {
        frontier(target, candidateTypes.collect { new Candidate(it) }, null)
    }

    /** A frontier asking for {@code target} whose directive declares a present {@code constant}. */
    static Frontier withConstant(final TypeMirror target, final String constant) {
        frontier(target, [], directive([], constant, null))
    }

    /**
     * A frontier asking for {@code target} whose directive declares a present {@code defaultValue}, with the given
     * candidate types in view (the source value being defaulted).
     */
    static Frontier withDefault(final TypeMirror target, final String defaultValue, final List<TypeMirror> candidateTypes) {
        frontier(target, candidateTypes.collect { new Candidate(it) }, directive([], null, defaultValue))
    }

    private static Frontier frontier(final TypeMirror target, final List<Candidate> candidates, final Directive directive) {
        new Frontier() {
            TypeMirror targetType() { target }
            Optional<Directive> directive() { Optional.ofNullable(directive) }
            List<Candidate> candidates() { candidates }
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
