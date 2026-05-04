package io.github.joke.percolate.processor;

import static java.util.stream.Collectors.toUnmodifiableList;
import static javax.lang.model.element.Modifier.ABSTRACT;

import com.google.auto.common.MoreElements;
import io.github.joke.percolate.processor.model.MapperShape;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
final class DiscoverAbstractMethods implements Stage {

    private final Elements elements;
    private final Types types;

    @Override
    public void run(MapperContext ctx) {
        MapperShape shape = apply(ctx.getMapperType());
        ctx.setShape(shape);
    }

    MapperShape apply(final TypeElement typeElement) {
        final var objectElement = elements.getTypeElement("java.lang.Object");
        final var rawMethods = MoreElements.getLocalAndInheritedMethods(typeElement, types, elements);
        return filter(typeElement, new ArrayList<>(rawMethods), objectElement);
    }

    MapperShape filter(
            final TypeElement typeElement, final List<ExecutableElement> methods, final TypeElement objectElement) {
        final var abstractMethods = methods.stream()
                .filter(this::isAbstract)
                .filter(m -> !isObjectMethod(m, objectElement))
                .collect(toUnmodifiableList());
        return new MapperShape(typeElement, abstractMethods);
    }

    boolean isAbstract(final ExecutableElement method) {
        return method.getModifiers().contains(ABSTRACT);
    }

    boolean isObjectMethod(final ExecutableElement method, final TypeElement objectElement) {
        final Element enclosing = method.getEnclosingElement();
        return enclosing != null && enclosing.equals(objectElement);
    }
}
