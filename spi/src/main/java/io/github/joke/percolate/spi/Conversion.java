package io.github.joke.percolate.spi;

import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

/**
 * Convenience base for the recurring target-driven <b>unary conversion</b> shape (design D1/D6): a strategy that, for a
 * demanded target, produces it from a single non-null input by an inline expression — boxing/unboxing, primitive
 * widening, and the like. The author supplies only {@link #conversions}: the input types that produce the target and,
 * per input, the rendering and weight. The base wires each into a one-port {@link OperationSpec} (a {@code NON_NULL}
 * {@code value} port of the input type producing the {@code NON_NULL} target), so the developer writes no port or spec
 * boilerplate and reads no candidate — the engine sources the port.
 *
 * <p>It is for <b>total, non-null</b> scalar conversions only; a producer with a nullable leg, a reuse-only input, a
 * multi-port signature, or a child scope is a different shape and implements {@link ExpansionStrategy} directly.
 */
public abstract class Conversion implements ExpansionStrategy {

    private static final String VALUE_ROLE = "value";

    /** The conversions that produce {@code target} from a single non-null input, or empty when none apply. */
    protected abstract Stream<Step> conversions(TypeMirror target, ResolveCtx ctx);

    @Override
    public final Stream<OperationSpec> expand(final Demand demand, final ResolveCtx ctx) {
        final var target = demand.targetType();
        return conversions(target, ctx)
                .map(step -> OperationSpec.of(
                        step.getLabel(),
                        step.getCodegen(),
                        step.getWeight(),
                        List.of(new Port(VALUE_ROLE, step.getInputType(), Nullability.NON_NULL)),
                        target,
                        Nullability.NON_NULL));
    }

    /** One non-null unary conversion producing the demanded target from {@link #getInputType()}. */
    @Value
    public static class Step {
        TypeMirror inputType;
        String label;
        int weight;
        OperationCodegen codegen;
    }
}
