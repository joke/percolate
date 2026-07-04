package io.github.joke.percolate.spi.types;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import lombok.experimental.UtilityClass;

/**
 * The pure {@code javax.lang.model} type → owned {@link TypeRef} conversion (change {@code evict-javax-model}): a
 * stateless structural mapping needing nothing but the mirror itself — no {@code ResolveCtx}, no compiler session
 * state — so any caller holding a {@link TypeMirror} (the discovery adapter, the graph's identity keying, or an
 * SPI strategy handed one via {@code Demand}) can derive its {@link TypeRef} directly. Because
 * {@link TypeRef#toString()} reproduces the Java source form, callers that keyed on {@code TypeMirror.toString()}
 * keep the same keys across the switch. v1 has no wildcard representation — a wildcard argument renders as its
 * upper bound ({@code ? extends X → X}, bare {@code ? → java.lang.Object}), matching the lawful-not-faithful
 * algebra.
 */
@UtilityClass
public class TypeRefs {

    private static final Map<TypeKind, PrimitiveKind> PRIMITIVE_KINDS = primitiveKinds();

    /** The {@link TypeRef} for {@code type} — the recursive structural conversion. */
    public TypeRef of(final TypeMirror type) {
        final var kind = type.getKind();
        if (kind == TypeKind.DECLARED) {
            return declaredRef((DeclaredType) type);
        }
        if (kind == TypeKind.ARRAY) {
            return TypeRef.array(of(((ArrayType) type).getComponentType()));
        }
        if (kind == TypeKind.TYPEVAR) {
            return TypeRef.variable(
                    ((TypeVariable) type).asElement().getSimpleName().toString());
        }
        if (kind == TypeKind.WILDCARD) {
            return wildcardRef((WildcardType) type);
        }
        if (kind == TypeKind.VOID || kind == TypeKind.NONE) {
            return TypeRef.none();
        }
        final var primitive = PRIMITIVE_KINDS.get(kind);
        if (primitive != null) {
            return TypeRef.primitive(primitive);
        }
        throw new IllegalArgumentException(
                "unsupported type shape (v1 has no wildcards/unions/intersections): " + type);
    }

    private TypeRef declaredRef(final DeclaredType type) {
        final var element = (TypeElement) type.asElement();
        final var args = type.getTypeArguments().stream().map(TypeRefs::of).collect(Collectors.toUnmodifiableList());
        return TypeRef.declared(element.getQualifiedName().toString(), args);
    }

    private TypeRef wildcardRef(final WildcardType wildcard) {
        final var extendsBound = wildcard.getExtendsBound();
        return extendsBound != null ? of(extendsBound) : TypeRef.declared("java.lang.Object");
    }

    @SuppressWarnings("PMD.UseConcurrentHashMap") // built once, frozen via Map.copyOf, never mutated after
    private Map<TypeKind, PrimitiveKind> primitiveKinds() {
        final Map<TypeKind, PrimitiveKind> kinds = new EnumMap<>(TypeKind.class);
        kinds.put(TypeKind.BOOLEAN, PrimitiveKind.BOOLEAN);
        kinds.put(TypeKind.BYTE, PrimitiveKind.BYTE);
        kinds.put(TypeKind.SHORT, PrimitiveKind.SHORT);
        kinds.put(TypeKind.CHAR, PrimitiveKind.CHAR);
        kinds.put(TypeKind.INT, PrimitiveKind.INT);
        kinds.put(TypeKind.LONG, PrimitiveKind.LONG);
        kinds.put(TypeKind.FLOAT, PrimitiveKind.FLOAT);
        kinds.put(TypeKind.DOUBLE, PrimitiveKind.DOUBLE);
        return Map.copyOf(kinds);
    }
}
