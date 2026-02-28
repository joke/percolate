package io.github.joke.percolate.spi.impl;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.graph.node.BoxingNode;
import io.github.joke.percolate.graph.node.UnboxingNode;
import io.github.joke.percolate.spi.ConversionFragment;
import io.github.joke.percolate.spi.ConversionProvider;
import io.github.joke.percolate.stage.MethodRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

@AutoService(ConversionProvider.class)
public final class PrimitiveWideningProvider implements ConversionProvider {

    private static final Map<TypeKind, List<TypeKind>> WIDENING;

    static {
        Map<TypeKind, List<TypeKind>> map = new HashMap<>();
        map.put(TypeKind.BYTE, asList(TypeKind.SHORT, TypeKind.INT, TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE));
        map.put(TypeKind.SHORT, asList(TypeKind.INT, TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE));
        map.put(TypeKind.CHAR, asList(TypeKind.INT, TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE));
        map.put(TypeKind.INT, asList(TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE));
        map.put(TypeKind.LONG, asList(TypeKind.FLOAT, TypeKind.DOUBLE));
        map.put(TypeKind.FLOAT, singletonList(TypeKind.DOUBLE));
        WIDENING = unmodifiableMap(map);
    }

    @Override
    public boolean canHandle(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        Types types = env.getTypeUtils();
        if (source.getKind().isPrimitive()) {
            return isWideningTarget(source, target) || isBoxedVersion(types, source, target);
        }
        return isUnboxedVersion(types, source, target);
    }

    @Override
    public ConversionFragment provide(
            TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env) {
        if (source.getKind().isPrimitive() && !target.getKind().isPrimitive()) {
            return ConversionFragment.of(new BoxingNode(source, target));
        }
        if (!source.getKind().isPrimitive() && target.getKind().isPrimitive()) {
            return ConversionFragment.of(new UnboxingNode(source, target));
        }
        return ConversionFragment.of();
    }

    private static boolean isWideningTarget(TypeMirror source, TypeMirror target) {
        return WIDENING.getOrDefault(source.getKind(), emptyList()).contains(target.getKind());
    }

    private static boolean isBoxedVersion(Types types, TypeMirror source, TypeMirror target) {
        TypeMirror boxed =
                types.boxedClass(types.getPrimitiveType(source.getKind())).asType();
        return types.isSameType(boxed, target);
    }

    private static boolean isUnboxedVersion(Types types, TypeMirror source, TypeMirror target) {
        try {
            TypeMirror unboxed = types.unboxedType(source);
            return types.isSameType(unboxed, target);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
