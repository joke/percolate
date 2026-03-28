package io.github.joke.percolate.processor.spi;

import io.github.joke.percolate.processor.model.FieldReadAccessor;
import io.github.joke.percolate.processor.model.FieldWriteAccessor;
import io.github.joke.percolate.processor.model.ReadAccessor;
import io.github.joke.percolate.processor.model.WriteAccessor;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public final class FieldDiscovery {

    private FieldDiscovery() {}

    static List<VariableElement> findPublicFields(final TypeMirror type) {
        final List<VariableElement> fields = new ArrayList<>();

        if (!(type instanceof DeclaredType)) {
            return fields;
        }

        final TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
        for (final var enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) {
                continue;
            }
            if (!enclosed.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }
            if (enclosed.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            fields.add((VariableElement) enclosed);
        }

        return fields;
    }

    public static final class Source implements SourcePropertyDiscovery {

        @Override
        public int priority() {
            return 50;
        }

        @Override
        public List<ReadAccessor> discover(final TypeMirror type, final Elements elements, final Types types) {
            final List<ReadAccessor> accessors = new ArrayList<>();
            for (final VariableElement field : findPublicFields(type)) {
                accessors.add(new FieldReadAccessor(field.getSimpleName().toString(), field.asType(), field));
            }
            return accessors;
        }
    }

    public static final class Target implements TargetPropertyDiscovery {

        @Override
        public int priority() {
            return 50;
        }

        @Override
        public List<WriteAccessor> discover(final TypeMirror type, final Elements elements, final Types types) {
            final List<WriteAccessor> accessors = new ArrayList<>();
            for (final VariableElement field : findPublicFields(type)) {
                accessors.add(new FieldWriteAccessor(field.getSimpleName().toString(), field.asType(), field));
            }
            return accessors;
        }
    }
}
