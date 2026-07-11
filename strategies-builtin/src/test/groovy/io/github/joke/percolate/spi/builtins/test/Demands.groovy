package io.github.joke.percolate.spi.builtins.test

import io.github.joke.percolate.spi.DescendDemand
import io.github.joke.percolate.spi.Directive
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.ProduceDemand

import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror

/**
 * Builds the myopic demand contexts the driver would hand a strategy, for unit specs that exercise a single strategy
 * in isolation. A {@link ProduceDemand} carries the demanded type and nullness, the in-effect {@code @Map}
 * {@link Directive}, the declared-children goal-spec set, the binding/slot name, and the nullness oracle; a
 * {@link DescendDemand} carries the concrete parent type, parent nullness, the single segment to resolve, and the
 * oracle. Neither exposes a candidate snapshot: the engine, not the strategy, sources every input port (design D1).
 */
final class Demands {

    private Demands() {
    }

    /**
     * A source-descent demand the driver hands an accessor strategy: the concrete parent type and the single member
     * segment to resolve. The {@code oracleNullness} is what the strategy's produced value is typed with.
     */
    static DescendDemand descend(final TypeMirror parent, final String seg,
                                 final Nullability oracleNullness = Nullability.NON_NULL) {
        new DescendDemand() {
            TypeMirror parentType() { parent }

            Nullability parentNullness() { Nullability.NON_NULL }

            String segment() { seg }

            @SuppressWarnings('UnusedMethodParameter')
            Nullability nullnessOf(final TypeMirror type, final Element scope) { oracleNullness }
        }
    }

    /** A bare demand asking for {@code target}, no directive. */
    static ProduceDemand forTarget(final TypeMirror target, final Nullability targetNullness = Nullability.NON_NULL) {
        demand(target, targetNullness, null, [] as Set, '', Nullability.NON_NULL)
    }

    /** An assembly demand asking for {@code target} whose declared-children goal spec is {@code declaredChildren}. */
    static ProduceDemand assembling(final TypeMirror target, final Set<String> declaredChildren) {
        demand(target, Nullability.NON_NULL, null, declaredChildren, '', Nullability.NON_NULL)
    }

    /** A demand asking for {@code target} whose directive declares a present {@code constant}. */
    static ProduceDemand withConstant(final TypeMirror target, final String constant) {
        demand(target, Nullability.NON_NULL, directive([], constant, null, null, null), [] as Set, '', Nullability.NON_NULL)
    }

    /**
     * A NON_NULL crossing demand for {@code target} naming {@code slot}, with an optional {@code defaultValue}
     * directive. Target-driven: the strategy reads only the demanded target/nullness, the directive, and the slot —
     * never a source candidate (the driver binds the reuse-only crossing ports).
     */
    static ProduceDemand crossing(final TypeMirror target, final String slot, final String defaultValue = null) {
        demand(target, Nullability.NON_NULL, directive([], null, defaultValue, null, null), [] as Set, slot, Nullability.NON_NULL)
    }

    /** A demand asking for {@code target} whose directive declares a present {@code format}, optionally with {@code zone}. */
    static ProduceDemand withFormat(final TypeMirror target, final String format, final String zone = null) {
        demand(target, Nullability.NON_NULL, directive([], null, null, format, zone), [] as Set, '', Nullability.NON_NULL)
    }

    /** A demand asking for {@code target} whose directive declares a present {@code zone}, with no {@code format}. */
    static ProduceDemand withZone(final TypeMirror target, final String zone) {
        demand(target, Nullability.NON_NULL, directive([], null, null, null, zone), [] as Set, '', Nullability.NON_NULL)
    }

    private static ProduceDemand demand(final TypeMirror target, final Nullability targetNullness,
                                        final Directive directive, final Set<String> declaredChildren,
                                        final String bindingName, final Nullability oracleNullness) {
        new ProduceDemand() {
            TypeMirror targetType() { target }

            Nullability targetNullness() { targetNullness }

            Optional<Directive> directive() { Optional.ofNullable(directive) }

            Set<String> declaredChildren() { declaredChildren }

            String bindingName() { bindingName }

            @SuppressWarnings('UnusedMethodParameter')
            Nullability nullnessOf(final TypeMirror type, final Element scope) { oracleNullness }
        }
    }

    private static Directive directive(final List<String> sourcePath, final String constant, final String defaultValue,
                                       final String format, final String zone) {
        new Directive() {
            List<String> sourcePath() { sourcePath }

            Optional<String> constant() { Optional.ofNullable(constant) }

            Optional<String> defaultValue() { Optional.ofNullable(defaultValue) }

            Optional<String> format() { Optional.ofNullable(format) }

            Optional<String> zone() { Optional.ofNullable(zone) }
        }
    }
}
