package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Conversion;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Boxing (JLS 5.1.7) and unboxing (JLS 5.1.8) as one concept — the primitive↔wrapper identity — authored
 * target-to-source on the {@link Conversion} archetype base. A wrapper target consumes its primitive (box); a primitive
 * target consumes its wrapper (unbox). Each is a single unary conversion; the engine composes longer chains (e.g.
 * {@code int → Long} as widen-then-box) through deduped intermediate Values.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class PrimitiveWrapperConversion extends Conversion {

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
    protected Stream<Step> conversions(final TypeMirror target, final ResolveCtx ctx) {
        if (ctx.isPrimitive(target)) {
            return Stream.of(unbox(target, ctx));
        }
        final var primitive = unboxedOrNull(target, ctx);
        return primitive == null ? Stream.empty() : Stream.of(box(target, primitive));
    }

    static Step box(final TypeMirror wrapperTarget, final TypeMirror primitive) {
        final OperationCodegen codegen = inputs -> CodeBlock.of("$T.valueOf($L)", wrapperTarget, inputs.single());
        return new Step(primitive, Labels.conversion(primitive, wrapperTarget), Weights.STEP, codegen);
    }

    static Step unbox(final TypeMirror primitiveTarget, final ResolveCtx ctx) {
        final TypeMirror wrapper = ctx.boxed(primitiveTarget);
        final var accessor = Objects.requireNonNull(UNBOX_ACCESSOR.get(ctx.kind(primitiveTarget)));
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L$Z.$N()", inputs.single(), accessor);
        return new Step(wrapper, Labels.conversion(wrapper, primitiveTarget), Weights.STEP, codegen);
    }

    /** The primitive a declared wrapper target unboxes to, or {@code null} when the target is not a wrapper. */
    @Nullable
    static TypeMirror unboxedOrNull(final TypeMirror target, final ResolveCtx ctx) {
        return ctx.asTypeElement(target)
                .map(element -> element.getQualifiedName().toString())
                .filter(WRAPPER_FQNS::contains)
                .<TypeMirror>map(fqn -> ctx.unboxed(target))
                .orElse(null);
    }
}
