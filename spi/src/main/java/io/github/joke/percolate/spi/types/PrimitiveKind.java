package io.github.joke.percolate.spi.types;

import java.util.Optional;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The eight Java primitive kinds with their source rendering and boxing counterparts — the model's fixed
 * primitive↔wrapper table (design D3: boxing is data, not a compiler query).
 */
@Getter
@RequiredArgsConstructor
public enum PrimitiveKind {
    BOOLEAN("boolean", "java.lang.Boolean"),
    BYTE("byte", "java.lang.Byte"),
    SHORT("short", "java.lang.Short"),
    CHAR("char", "java.lang.Character"),
    INT("int", "java.lang.Integer"),
    LONG("long", "java.lang.Long"),
    FLOAT("float", "java.lang.Float"),
    DOUBLE("double", "java.lang.Double");

    private final String sourceName;
    private final String boxedName;

    /** The kind whose wrapper is the type named {@code qualifiedName}, or empty for a non-wrapper name. */
    public static Optional<PrimitiveKind> ofBoxedName(final String qualifiedName) {
        return Stream.of(values())
                .filter(kind -> kind.boxedName.equals(qualifiedName))
                .findFirst();
    }
}
