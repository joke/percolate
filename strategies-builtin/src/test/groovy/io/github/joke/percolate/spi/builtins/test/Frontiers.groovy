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
        new Frontier() {
            TypeMirror targetType() { parentType }
            Optional<Directive> directive() { Optional.of(segmentDirective(segment)) }
            List<Candidate> candidates() { [new Candidate(parentType)] }
        }
    }

    /** A bare frontier asking for {@code target}, with no directive and the given candidate types in view. */
    static Frontier forTarget(final TypeMirror target, final List<TypeMirror> candidateTypes = []) {
        new Frontier() {
            TypeMirror targetType() { target }
            Optional<Directive> directive() { Optional.empty() }
            List<Candidate> candidates() { candidateTypes.collect { new Candidate(it) } }
        }
    }

    private static Directive segmentDirective(final String segment) {
        new Directive() {
            List<String> sourcePath() { [segment] }

            @SuppressWarnings('UnusedMethodParameter')
            Optional<String> attribute(final String name) { Optional.empty() }
        }
    }
}
