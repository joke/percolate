package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Demand;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.TypeProbe;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Boxing (JLS 5.1.7) and unboxing (JLS 5.1.8) as one concept — the primitive↔wrapper identity — authored
 * target-to-source. A wrapper target consumes its primitive (box); a primitive target consumes its wrapper
 * (unbox). Each emits a single unary {@link OperationSpec}; the engine composes longer chains (e.g.
 * {@code int → Long} as widen-then-box) through deduped intermediate Values.
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
    public Stream<OperationSpec> expand(final Demand demand, final ResolveCtx ctx) {
        final var target = demand.targetType();
        if (target.getKind().isPrimitive()) {
            return unbox(target, ctx);
        }
        final var primitive = unboxedOrNull(target, ctx);
        return primitive == null ? Stream.empty() : box(target, primitive);
    }

    private static Stream<OperationSpec> box(final TypeMirror wrapperTarget, final TypeMirror primitive) {
        final OperationCodegen codegen = inputs -> CodeBlock.of("$T.valueOf($L)", wrapperTarget, inputs.single());
        return Stream.of(conversionSpec(primitive, wrapperTarget, codegen));
    }

    private static Stream<OperationSpec> unbox(final TypeMirror primitiveTarget, final ResolveCtx ctx) {
        final TypeMirror wrapper =
                ctx.types().boxedClass((PrimitiveType) primitiveTarget).asType();
        final var accessor = Objects.requireNonNull(UNBOX_ACCESSOR.get(primitiveTarget.getKind()));
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L.$N()", inputs.single(), accessor);
        return Stream.of(conversionSpec(wrapper, primitiveTarget, codegen));
    }

    private static OperationSpec conversionSpec(
            final TypeMirror inputType, final TypeMirror output, final OperationCodegen codegen) {
        final var port = new Port("value", inputType, Nullability.NON_NULL);
        return OperationSpec.of(
                Labels.conversion(inputType, output),
                codegen,
                Weights.STEP,
                List.of(port),
                output,
                Nullability.NON_NULL);
    }

    /** The primitive a declared wrapper target unboxes to, or {@code null} when the target is not a wrapper. */
    @Nullable
    private static TypeMirror unboxedOrNull(final TypeMirror target, final ResolveCtx ctx) {
        return TypeProbe.asTypeElement(target, ctx)
                .map(element -> element.getQualifiedName().toString())
                .filter(WRAPPER_FQNS::contains)
                .<TypeMirror>map(fqn -> ctx.types().unboxedType(target))
                .orElse(null);
    }
}
