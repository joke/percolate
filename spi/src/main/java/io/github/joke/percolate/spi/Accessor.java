package io.github.joke.percolate.spi;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

/**
 * Convenience base for the recurring target-driven <b>source-accessor</b> shape (design D6, source-path resolution): a
 * strategy that resolves one {@code @Map} source-path segment to a member read on the parent type — a getter, a fluent
 * method, or a visible field. The accessor surface is <b>directive-pinned</b>: the driver pins the parent type as
 * {@link Demand#targetType()} and the single segment to descend in {@link Demand#directive()}. The base reads the
 * segment, resolves the parent {@link TypeElement} (declining a non-declared parent), wires the one-port accessor
 * {@link OperationSpec} (a {@code NON_NULL} {@code value} port of the parent type), and types the produced value's
 * nullness through the demand oracle — so the author supplies only {@link #accessor}: the member match and its
 * rendering. It reads no candidate.
 *
 * <p>A strategy fires only when the demand carries a one-segment directive source path; otherwise it produces nothing.
 */
public abstract class Accessor implements ExpansionStrategy {

    private static final int SINGLE_SEGMENT = 1;
    private static final String VALUE_ROLE = "value";

    /** Resolve the one source-path {@code segment} to an accessor on {@code parent}, or empty when none matches. */
    protected abstract Optional<Step> accessor(TypeElement parent, String segment, ResolveCtx ctx);

    @Override
    public final Stream<OperationSpec> expand(final Demand demand, final ResolveCtx ctx) {
        return segment(demand)
                .flatMap(seg ->
                        TypeProbe.asTypeElement(demand.targetType(), ctx).flatMap(parent -> accessor(parent, seg, ctx)))
                .map(step -> toSpec(step, demand))
                .stream();
    }

    private OperationSpec toSpec(final Step step, final Demand demand) {
        final var port = new Port(VALUE_ROLE, demand.targetType(), Nullability.NON_NULL);
        final var nullness = demand.nullnessOf(step.getOutputType(), step.getMember());
        return OperationSpec.of(
                step.getLabel(), step.getCodegen(), step.getWeight(), List.of(port), step.getOutputType(), nullness);
    }

    private static Optional<String> segment(final Demand demand) {
        return demand.directive()
                .map(Directive::sourcePath)
                .filter(path -> path.size() == SINGLE_SEGMENT)
                .map(path -> path.get(0));
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
