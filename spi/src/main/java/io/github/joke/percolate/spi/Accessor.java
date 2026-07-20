package io.github.joke.percolate.spi;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import lombok.Value;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Convenience base for the recurring <b>source-accessor</b> shape (design D6, source-path resolution): a strategy that
 * resolves one {@code @Map} source-path segment to a member read on the parent type — a getter, a fluent method, or a
 * visible field. Accessors answer the <b>descend</b> question: the driver hands a {@link DescendDemand} carrying the
 * concrete parent type ({@link DescendDemand#parentType()}) and the single {@link DescendDemand#segment()} to resolve.
 * The base resolves the parent {@link TypeElement} (declining a non-declared parent), wires the one-port accessor
 * {@link OperationSpec} (a {@code NON_NULL} {@code value} port of the parent type), and types the produced value's
 * nullness through the demand oracle — so the author supplies only {@link #accessor}: the member match and its
 * rendering. The produced output type is the strategy's answer, discovered from the member. It reads no candidate.
 */
public abstract class Accessor implements ExpansionStrategy {

    private static final String VALUE_ROLE = "value";

    /** Resolve the one source-path {@code segment} to an accessor on {@code parent}, or empty when none matches. */
    protected abstract Optional<Step> accessor(TypeElement parent, String segment, ResolveCtx ctx);

    @Override
    public final Stream<OperationSpec> descend(final DescendDemand demand, final ResolveCtx ctx) {
        return ctx
                .asTypeElement(demand.parentType())
                .flatMap(parent -> accessor(parent, demand.segment(), ctx))
                .map(step -> toSpec(step, demand))
                .stream();
    }

    @VisibleForTesting
    protected OperationSpec toSpec(final Step step, final DescendDemand demand) {
        final var port = new Port(VALUE_ROLE, demand.parentType(), Nullability.NON_NULL);
        final var nullness = demand.nullnessOf(step.getOutputType(), step.getMember());
        return OperationSpec.of(
                step.getLabel(), step.getCodegen(), step.getWeight(), List.of(port), step.getOutputType(), nullness);
    }

    /** One resolved member access: the produced type and member (for the nullness oracle), debug label, weight, code. */
    @Value
    public static class Step {
        TypeMirror outputType;
        Element member;
        String label;
        int weight;
        OperationCodegen codegen;
    }
}
