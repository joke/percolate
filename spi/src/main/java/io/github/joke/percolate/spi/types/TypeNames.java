package io.github.joke.percolate.spi.types;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeVariableName;
import lombok.experimental.UtilityClass;

/**
 * The {@link TypeRef} → JavaPoet {@link TypeName} emitter (design D7) — replaces {@code TypeName.get(TypeMirror)}
 * as codegen's way into JavaPoet. Its output is golden-compared against JavaPoet's own mirror-based rendering.
 *
 * <p>SPIKE scope: {@link ClassName#bestGuess} for declared names (the Phase-3 emitter resolves nesting through
 * the declaration's enclosing chain); no type-use nullability annotations yet.
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
