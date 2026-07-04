package io.github.joke.percolate.spi.types;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeVariableName;
import lombok.experimental.UtilityClass;

/**
 * The {@link TypeRef} → JavaPoet {@link TypeName} emitter (design D7) — an alternative to
 * {@code TypeName.get(TypeMirror)} for codegen sites whose type is known wildcard-free by construction. Its
 * output is golden-compared against JavaPoet's own mirror-based rendering ({@code TypeNamesSpec}).
 *
 * <p><b>Scope (design D7 amendment):</b> v1's {@link TypeRef} has no wildcard representation (design D3), so this
 * emitter is <em>not</em> a safe drop-in replacement everywhere {@code TypeName.get(TypeMirror)} appears — a
 * wildcard-bearing source type would silently render its upper bound, which breaks any site requiring exact JLS
 * fidelity (a method override signature, a hoisted local's declared type). Those sites stay on
 * {@code TypeName.get(TypeMirror)} permanently. Nested-class resolution uses {@link ClassName#bestGuess}, which
 * is correct under the standard Java naming convention (lowercase packages, uppercase classes) but is a
 * heuristic, not a walk of the real enclosing-element chain; no type-use nullability annotations yet.
 */
@UtilityClass
public class TypeNames {

    /** The JavaPoet {@link TypeName} for {@code type}. */
    public TypeName toTypeName(final TypeRef type) {
        if (type instanceof TypeRef.Primitive) {
            return primitiveName(((TypeRef.Primitive) type).getKind());
        }
        if (type instanceof TypeRef.Array) {
            return ArrayTypeName.of(toTypeName(((TypeRef.Array) type).getComponent()));
        }
        if (type instanceof TypeRef.Variable) {
            return TypeVariableName.get(((TypeRef.Variable) type).getName());
        }
        if (type instanceof TypeRef.Declared) {
            return declaredName((TypeRef.Declared) type);
        }
        return TypeName.VOID;
    }

    private TypeName declaredName(final TypeRef.Declared type) {
        final var className = ClassName.bestGuess(type.getQualifiedName());
        if (type.getArgs().isEmpty()) {
            return className;
        }
        final var args = type.getArgs().stream().map(TypeNames::toTypeName).toArray(TypeName[]::new);
        return ParameterizedTypeName.get(className, args);
    }

    private TypeName primitiveName(final PrimitiveKind kind) {
        switch (kind) {
            case BOOLEAN:
                return TypeName.BOOLEAN;
            case BYTE:
                return TypeName.BYTE;
            case SHORT:
                return TypeName.SHORT;
            case CHAR:
                return TypeName.CHAR;
            case INT:
                return TypeName.INT;
            case LONG:
                return TypeName.LONG;
            case FLOAT:
                return TypeName.FLOAT;
            default:
                return TypeName.DOUBLE;
        }
    }
}
