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
 * <p>Construct via {@link #of} (a plain operation) or {@link #mapping} (a scope-owning element mapping).
 */
@Value
public class OperationSpec {

    Codegen codegen;
    int weight;
    List<Port> ports;
    TypeMirror outputType;
    Nullability outputNullness;
    Optional<ChildScopeSpec> childScope;

    /** A plain operation (constructor, accessor, conversion, constant, wrap/unwrap): no child scope. */
    public static OperationSpec of(
            final Codegen codegen,
            final int weight,
            final List<Port> ports,
            final TypeMirror outputType,
            final Nullability outputNullness) {
        return new OperationSpec(codegen, weight, List.copyOf(ports), outputType, outputNullness, Optional.empty());
    }

    /** A scope-owning element mapping (container map): its child scope carries the per-element transform. */
    public static OperationSpec mapping(
            final Codegen codegen,
            final int weight,
            final List<Port> ports,
            final TypeMirror outputType,
            final Nullability outputNullness,
            final ChildScopeSpec childScope) {
        return new OperationSpec(
                codegen, weight, List.copyOf(ports), outputType, outputNullness, Optional.of(childScope));
    }
}
