package io.github.joke.percolate.processor.internal.graph;

import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.types.TypeRefs;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import lombok.Getter;

/**
 * A typed variable at a {@link Location}: the OR-kind vertex of the bipartite graph — it is producible by any
 * one of its inbound producer {@link Operation}s. Identity is dedup-by-key: {@code MapperGraph.valueFor} get-or-
 * creates one instance per {@code (scope, location, type, nullness)}, so type-identical demands share a Value
 * and type- or nullness-divergent demands stay distinct (nullness is part of identity — under JSpecify,
 * {@code String!} and {@code String?} are different types).
 *
 * <p>Type and nullness are write-once (unknown → determined → frozen), set together at the single mutation
 * site. A Value carries no group labels, no directive, no codegen, and no weight.
 */
@Getter
public final class Value implements GraphVertex {

    private static final String UNKNOWN = "?";

    private final Location loc;
    private final Scope scope;
    private Optional<TypeMirror> type;
    private Optional<Nullability> nullness;

    Value(
            final Location loc,
            final Scope scope,
            final Optional<TypeMirror> type,
            final Optional<Nullability> nullness) {
        this.loc = loc;
        this.scope = scope;
        this.type = type;
        this.nullness = nullness;
    }

    public void setTyping(final TypeMirror newType, final Nullability newNullness) {
        if (type.isPresent() || nullness.isPresent()) {
            throw new IllegalStateException(
                    "Value typing is already set; setTyping() requires both type and nullness to be empty");
        }
        this.type = Optional.of(newType);
        this.nullness = Optional.of(newNullness);
    }

    @Override
    public String id() {
        return scope.encode() + "::" + loc.segment() + "::" + typeEncode() + "::" + nullnessEncode();
    }

    private String typeEncode() {
        return type.map(mirror -> TypeRefs.of(mirror).toString()).orElse(UNKNOWN);
    }

    private String nullnessEncode() {
        return nullness.map(Enum::name).orElse(UNKNOWN);
    }

    @Override
    public boolean equals(final Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
