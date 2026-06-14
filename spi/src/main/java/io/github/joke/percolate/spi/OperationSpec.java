package io.github.joke.percolate.spi;

import java.util.List;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

/**
 * What an {@link ExpansionStrategy} match offers: a single production, shaped as plain data. It carries the
 * operation's {@link Codegen} handle, its {@code weight}, its ordered {@link Port} signature (an AND over the
 * inputs it consumes), the produced output type and {@link Nullability}, and optionally a {@link ChildScopeSpec}
 * (present only for a container element mapping — a scope-owning operation). The driver turns one spec into one
 * atomic {@code AddOperation} delta, fanning a demand out per port. A spec exposes no graph or engine surface;
 * strategies stay myopic.
 *
 * <p>Construct via {@link #of} (a plain total operation), {@link #ofPartial} (a plain operation that may throw on
 * a structurally-valid input, e.g. {@code Optional.orElseThrow} — a {@code partial} producer the plan-extraction
 * totality rule deprioritises), or {@link #mapping} (a scope-owning element mapping).
 */
@Value
public class OperationSpec {

    Codegen codegen;
    int weight;
    List<Port> ports;
    TypeMirror outputType;
    Nullability outputNullness;
    Optional<ChildScopeSpec> childScope;
    boolean partial;

    /** A plain total operation (constructor, accessor, conversion, constant, wrap, iterate, collect): no child scope. */
    public static OperationSpec of(
            final Codegen codegen,
            final int weight,
            final List<Port> ports,
            final TypeMirror outputType,
            final Nullability outputNullness) {
        return new OperationSpec(
                codegen, weight, List.copyOf(ports), outputType, outputNullness, Optional.empty(), false);
    }

    /** A plain partial operation (may throw on a structurally-valid input, e.g. {@code Optional.orElseThrow}). */
    public static OperationSpec ofPartial(
            final Codegen codegen,
            final int weight,
            final List<Port> ports,
            final TypeMirror outputType,
            final Nullability outputNullness) {
        return new OperationSpec(
                codegen, weight, List.copyOf(ports), outputType, outputNullness, Optional.empty(), true);
    }

    /** A scope-owning element mapping (stream map/flatMap, Optional.map): its child scope carries the transform. */
    public static OperationSpec mapping(
            final Codegen codegen,
            final int weight,
            final List<Port> ports,
            final TypeMirror outputType,
            final Nullability outputNullness,
            final ChildScopeSpec childScope) {
        return new OperationSpec(
                codegen, weight, List.copyOf(ports), outputType, outputNullness, Optional.of(childScope), false);
    }
}
