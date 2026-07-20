package io.github.joke.percolate.reactor.test

import io.github.joke.percolate.spi.Directive
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.ProduceDemand

import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror

/**
 * Builds the myopic {@link ProduceDemand} the driver would hand a producer strategy, for unit specs that exercise a
 * single {@code reactor} strategy in isolation. Mirrors {@code strategies-builtin}'s test-only {@code Demands}
 * helper (not shared across modules, so re-declared here) narrowed to what the reactor strategies actually read:
 * only the demanded target type — none of them consult the directive, declared-children set, binding name, or
 * nullness oracle.
 */
final class Demands {

    private Demands() {
    }

    /** A bare demand asking for {@code target}, {@code NON_NULL}, no directive, no declared children, no slot name. */
    static ProduceDemand forTarget(final TypeMirror target) {
        new ProduceDemand() {
            TypeMirror targetType() { target }

            Nullability targetNullness() { Nullability.NON_NULL }

            Optional<Directive> directive() { Optional.empty() }

            Set<String> declaredChildren() { [] as Set }

            String bindingName() { '' }

            @SuppressWarnings('UnusedMethodParameter')
            Nullability nullnessOf(final TypeMirror type, final Element scope) { Nullability.NON_NULL }
        }
    }
}
