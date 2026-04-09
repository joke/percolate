package io.github.joke.percolate.processor.spi;

import static java.util.stream.Collectors.toUnmodifiableList;
import static javax.lang.model.element.ElementKind.FIELD;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.processor.model.FieldReadAccessor;
import io.github.joke.percolate.processor.model.FieldWriteAccessor;
import io.github.joke.percolate.processor.model.ReadAccessor;
import io.github.joke.percolate.processor.model.WriteAccessor;
import java.util.List;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.experimental.UtilityClass;

@UtilityClass
public class FieldDiscovery {

    static List<VariableElement> findPublicFields(final TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return List.of();
        }

        final TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
        return typeElement.getEnclosedElements().stream()
                .filter(e -> e.getKind() == FIELD)
                .filter(e -> e.getModifiers().contains(PUBLIC))
                .filter(e -> !e.getModifiers().contains(STATIC))
                .map(VariableElement.class::cast)
                .collect(toUnmodifiableList());
    }

    @AutoService(SourcePropertyDiscovery.class)
    public static final class Source implements SourcePropertyDiscovery {

        @Override
        public int priority() {
            return 50;
        }

        @Override
        public List<ReadAccessor> discover(final TypeMirror type, final Elements elements, final Types types) {
            return findPublicFields(type).stream()
                    .map(field -> (ReadAccessor)
                            new FieldReadAccessor(field.getSimpleName().toString(), field.asType(), field))
                    .collect(toUnmodifiableList());
        }
    }

    @AutoService(TargetPropertyDiscovery.class)
    public static final class Target implements TargetPropertyDiscovery {

        @Override
        public int priority() {
            return 50;
        }

        @Override
        public List<WriteAccessor> discover(final TypeMirror type, final Elements elements, final Types types) {
            return findPublicFields(type).stream()
                    .map(field -> (WriteAccessor)
                            new FieldWriteAccessor(field.getSimpleName().toString(), field.asType(), field))
                    .collect(toUnmodifiableList());
        }
    }
}
