package io.github.joke.percolate.spi.impl;

import static java.util.Collections.emptyList;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.graph.edge.ConversionEdge;
import io.github.joke.percolate.spi.ConversionProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;

@AutoService(ConversionProvider.class)
public final class PrimitiveWideningProvider implements ConversionProvider {

    private static final Map<TypeKind, List<TypeKind>> WIDENING;

    static {
        Map<TypeKind, List<TypeKind>> map = new HashMap<>();
        map.put(TypeKind.BYTE, Collections.unmodifiableList(Arrays.asList(TypeKind.SHORT, TypeKind.INT, TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE)));
        map.put(TypeKind.SHORT, Collections.unmodifiableList(Arrays.asList(TypeKind.INT, TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE)));
        map.put(TypeKind.CHAR, Collections.unmodifiableList(Arrays.asList(TypeKind.INT, TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE)));
        map.put(TypeKind.INT, Collections.unmodifiableList(Arrays.asList(TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE)));
        map.put(TypeKind.LONG, Collections.unmodifiableList(Arrays.asList(TypeKind.FLOAT, TypeKind.DOUBLE)));
        map.put(TypeKind.FLOAT, Collections.unmodifiableList(Collections.singletonList(TypeKind.DOUBLE)));
        WIDENING = Collections.unmodifiableMap(map);
    }

    @Override
    public List<Conversion> possibleConversions(TypeMirror source, @Nullable ProcessingEnvironment env) {
        if (env == null) {
            return emptyList();
        }
        Types types = env.getTypeUtils();
        List<Conversion> result = new ArrayList<>();

        if (source.getKind().isPrimitive()) {
            List<TypeKind> targets = WIDENING.getOrDefault(source.getKind(), emptyList());
            for (TypeKind targetKind : targets) {
                TypeMirror targetType = types.getPrimitiveType(targetKind);
                result.add(new Conversion(targetType,
                        new ConversionEdge(ConversionEdge.Kind.PRIMITIVE_WIDEN, source, targetType, "$expr")));
            }
            TypeMirror boxed = types.boxedClass(types.getPrimitiveType(source.getKind())).asType();
            result.add(new Conversion(boxed,
                    new ConversionEdge(ConversionEdge.Kind.PRIMITIVE_BOX, source, boxed, "$expr")));
        } else {
            try {
                TypeMirror unboxed = types.unboxedType(source);
                result.add(new Conversion(unboxed,
                        new ConversionEdge(ConversionEdge.Kind.PRIMITIVE_UNBOX, source, unboxed, "$expr")));
            } catch (IllegalArgumentException ignored) {
                // Not a boxed type
            }
        }
        return Collections.unmodifiableList(result);
    }
}
