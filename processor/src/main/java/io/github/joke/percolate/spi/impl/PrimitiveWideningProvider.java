package io.github.joke.percolate.spi.impl;

import static java.util.Collections.emptyList;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static javax.lang.model.type.TypeKind.BYTE;
import static javax.lang.model.type.TypeKind.CHAR;
import static javax.lang.model.type.TypeKind.DOUBLE;
import static javax.lang.model.type.TypeKind.FLOAT;
import static javax.lang.model.type.TypeKind.INT;
import static javax.lang.model.type.TypeKind.LONG;
import static javax.lang.model.type.TypeKind.SHORT;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.graph.node.BoxingNode;
import io.github.joke.percolate.graph.node.UnboxingNode;
import io.github.joke.percolate.spi.ConversionFragment;
import io.github.joke.percolate.spi.ConversionProvider;
import io.github.joke.percolate.stage.MethodRegistry;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

@AutoService(ConversionProvider.class)
public final class PrimitiveWideningProvider implements ConversionProvider {

    private static final Map<TypeKind, List<TypeKind>> WIDENING = ofEntries(
            entry(BYTE, List.of(SHORT, INT, LONG, FLOAT, DOUBLE)),
            entry(SHORT, List.of(INT, LONG, FLOAT, DOUBLE)),
            entry(CHAR, List.of(INT, LONG, FLOAT, DOUBLE)),
            entry(INT, List.of(LONG, FLOAT, DOUBLE)),
            entry(LONG, List.of(FLOAT, DOUBLE)),
            entry(FLOAT, List.of(DOUBLE)));

    @Override
    public boolean canHandle(
            final TypeMirror source,
            final TypeMirror target,
            final MethodRegistry registry,
            final ProcessingEnvironment env) {
        final var types = env.getTypeUtils();
        if (source.getKind().isPrimitive()) {
            return isWideningTarget(source, target) || isBoxedVersion(types, source, target);
        }
        return isUnboxedVersion(types, source, target);
    }

    @Override
    public ConversionFragment provide(
            final TypeMirror source,
            final TypeMirror target,
            final MethodRegistry registry,
            final ProcessingEnvironment env) {
        if (source.getKind().isPrimitive() && !target.getKind().isPrimitive()) {
            return ConversionFragment.of(new BoxingNode(source, target));
        }
        if (!source.getKind().isPrimitive() && target.getKind().isPrimitive()) {
            return ConversionFragment.of(new UnboxingNode(source, target));
        }
        return ConversionFragment.of();
    }

    private static boolean isWideningTarget(final TypeMirror source, final TypeMirror target) {
        return WIDENING.getOrDefault(source.getKind(), emptyList()).contains(target.getKind());
    }

    private static boolean isBoxedVersion(final Types types, final TypeMirror source, final TypeMirror target) {
        final var boxed =
                types.boxedClass(types.getPrimitiveType(source.getKind())).asType();
        return types.isSameType(boxed, target);
    }

    private static boolean isUnboxedVersion(final Types types, final TypeMirror source, final TypeMirror target) {
        try {
            final var unboxed = types.unboxedType(source);
            return types.isSameType(unboxed, target);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
