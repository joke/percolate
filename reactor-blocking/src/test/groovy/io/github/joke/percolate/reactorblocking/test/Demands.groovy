package io.github.joke.percolate.reactorblocking.test

import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.ProduceDemand

import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror

/**
 * Builds the myopic {@link ProduceDemand} context the driver would hand a blocking bridge strategy, for unit specs
 * that exercise a single strategy in isolation — mirroring {@code strategies-builtin}'s
 * {@code io.github.joke.percolate.spi.builtins.test.Demands}. Every blocking strategy in this module reads only the
 * demanded target type, so {@link #forTarget} is the sole factory needed here.
 */
final class Demands {

    private Demands() {
    }

    /** A bare demand asking for {@code target}, no directive, no declared children, no named slot. */
    static ProduceDemand forTarget(final TypeMirror target, final Nullability targetNullness = Nullability.NON_NULL) {
        new ProduceDemand() {
            TypeMirror targetType() { target }

            Nullability targetNullness() { targetNullness }

            Optional<io.github.joke.percolate.spi.Directive> directive() { Optional.empty() }

            Set<String> declaredChildren() { [] as Set }

            String bindingName() { '' }

            @SuppressWarnings('UnusedMethodParameter')
            Nullability nullnessOf(final TypeMirror type, final Element scope) { Nullability.NON_NULL }
        }
    }
}
