package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.ExpansionStep;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Frontier;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Slot;
import io.github.joke.percolate.spi.Weights;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Boxing (JLS 5.1.7) and unboxing (JLS 5.1.8) as one concept — the primitive↔wrapper identity — authored
 * target-to-source. A wrapper target consumes its primitive (box); a primitive target consumes its wrapper
 * (unbox). Each emits a single {@code CONVERSION} step that re-types the same value in place; the engine composes
 * longer chains (e.g. {@code int → Long} as widen-then-box) by synthesizing the intermediate type node.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class PrimitiveWrapperConversion implements ExpansionStrategy {

    private static final Set<String> WRAPPER_FQNS = Set.of(
            "java.lang.Boolean",
            "java.lang.Byte",
            "java.lang.Short",
            "java.lang.Character",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Float",
            "java.lang.Double");

    private static final Map<TypeKind, String> UNBOX_ACCESSOR = Map.of(
            TypeKind.BOOLEAN, "booleanValue",
            TypeKind.BYTE, "byteValue",
            TypeKind.SHORT, "shortValue",
            TypeKind.CHAR, "charValue",
            TypeKind.INT, "intValue",
            TypeKind.LONG, "longValue",
            TypeKind.FLOAT, "floatValue",
            TypeKind.DOUBLE, "doubleValue");

    @Override
    public Stream<ExpansionStep> expand(final Frontier frontier, final ResolveCtx ctx) {
        final var target = frontier.targetType();
        if (target.getKind().isPrimitive()) {
            return unbox(target, ctx);
        }
        final var primitive = unboxedOrNull(target, ctx);
        return primitive == null ? Stream.empty() : box(target, primitive);
    }

    private static Stream<ExpansionStep> box(final TypeMirror wrapperTarget, final TypeMirror primitive) {
        final EdgeCodegen codegen = (vars, inputs) -> CodeBlock.of("$T.valueOf($L)", wrapperTarget, inputs.single());
        return Stream.of(conversionStep(primitive, wrapperTarget, codegen));
    }

    private static Stream<ExpansionStep> unbox(final TypeMirror primitiveTarget, final ResolveCtx ctx) {
        final TypeMirror wrapper =
                ctx.types().boxedClass((PrimitiveType) primitiveTarget).asType();
        final var accessor = Objects.requireNonNull(UNBOX_ACCESSOR.get(primitiveTarget.getKind()));
        final EdgeCodegen codegen = (vars, inputs) -> CodeBlock.of("$L.$N()", inputs.single(), accessor);
        return Stream.of(conversionStep(wrapper, primitiveTarget, codegen));
    }

    private static ExpansionStep conversionStep(
            final TypeMirror inputType, final TypeMirror output, final EdgeCodegen codegen) {
        final var input = new Slot("value", inputType, Weights.NOOP, null);
        return ExpansionStep.conversion(input, output, codegen, Weights.STEP);
    }

    /** The primitive a declared wrapper target unboxes to, or {@code null} when the target is not a wrapper. */
    @Nullable
    private static TypeMirror unboxedOrNull(final TypeMirror target, final ResolveCtx ctx) {
        if (target.getKind() != TypeKind.DECLARED) {
            return null;
        }
        final var element = ((DeclaredType) target).asElement();
        if (!(element instanceof TypeElement)) {
            return null;
        }
        final var fqn = ((TypeElement) element).getQualifiedName().toString();
        return WRAPPER_FQNS.contains(fqn) ? ctx.types().unboxedType(target) : null;
    }
}
