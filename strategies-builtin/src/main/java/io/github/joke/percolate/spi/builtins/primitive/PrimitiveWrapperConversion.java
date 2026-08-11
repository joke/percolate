package io.github.joke.percolate.spi.builtins.primitive;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Conversion;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.builtins.Labels;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import static io.github.joke.percolate.spi.Weights.STEP;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.type.TypeKind.BOOLEAN;
import static javax.lang.model.type.TypeKind.BYTE;
import static javax.lang.model.type.TypeKind.CHAR;
import static javax.lang.model.type.TypeKind.DOUBLE;
import static javax.lang.model.type.TypeKind.FLOAT;
import static javax.lang.model.type.TypeKind.LONG;
import static javax.lang.model.type.TypeKind.SHORT;

// Boxing (JLS 5.1.7) and unboxing (JLS 5.1.8) as one concept — the primitive↔wrapper identity — authored
// target-to-source on the Conversion archetype base. A wrapper target consumes its primitive (box); a primitive
// target consumes its wrapper (unbox). Each is a single unary conversion; the engine composes longer chains
// (e.g. int → Long as widen-then-box) through deduped intermediate Values.
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
            BOOLEAN,
            "booleanValue",
            BYTE,
            "byteValue",
            SHORT,
            "shortValue",
            CHAR,
            "charValue",
            TypeKind.INT,
            "intValue",
            LONG,
            "longValue",
            FLOAT,
            "floatValue",
            DOUBLE,
            "doubleValue");

    @Override
    @VisibleForTesting
    protected Stream<Step> conversions(final TypeMirror target, final ResolveCtx ctx) {
        if (ctx.isPrimitive(target)) {
            return Stream.of(unbox(target, ctx));
        }
        final var primitive = unboxedOrNull(target, ctx);
        return primitive == null ? Stream.empty() : Stream.of(box(target, primitive));
    }

    Step box(final TypeMirror wrapperTarget, final TypeMirror primitive) {
        final OperationCodegen codegen = inputs -> CodeBlock.of("$T.valueOf($L)", wrapperTarget, inputs.single());
        return new Step(primitive, Labels.conversion(primitive, wrapperTarget), STEP, codegen);
    }

    Step unbox(final TypeMirror primitiveTarget, final ResolveCtx ctx) {
        final var wrapper = ctx.boxed(primitiveTarget);
        final var accessor = requireNonNull(UNBOX_ACCESSOR.get(ctx.kind(primitiveTarget)));
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L$Z.$N()", inputs.single(), accessor);
        return new Step(wrapper, Labels.conversion(wrapper, primitiveTarget), STEP, codegen);
    }

    // The primitive a declared wrapper target unboxes to, or null when the target is not a wrapper.
    @Nullable
    TypeMirror unboxedOrNull(final TypeMirror target, final ResolveCtx ctx) {
        return ctx.asTypeElement(target)
                .map(element -> element.getQualifiedName().toString())
                .filter(WRAPPER_FQNS::contains)
                .<TypeMirror>map(fqn -> ctx.unboxed(target))
                .orElse(null);
    }
}
