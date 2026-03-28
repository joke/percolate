package io.github.joke.percolate.processor.spi;

import io.github.joke.percolate.processor.model.ConstructorParamAccessor;
import io.github.joke.percolate.processor.model.WriteAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public final class ConstructorDiscovery implements TargetPropertyDiscovery {

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public List<WriteAccessor> discover(final TypeMirror type, final Elements elements, final Types types) {
        final List<WriteAccessor> accessors = new ArrayList<>();

        if (!(type instanceof DeclaredType)) {
            return accessors;
        }

        final TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();

        final ExecutableElement constructor = typeElement.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
                .map(ExecutableElement.class::cast)
                .max(Comparator.comparingInt(c -> c.getParameters().size()))
                .orElse(null);

        if (constructor == null) {
            return accessors;
        }

        final List<? extends VariableElement> params = constructor.getParameters();
        for (int i = 0; i < params.size(); i++) {
            final VariableElement param = params.get(i);
            accessors.add(
                    new ConstructorParamAccessor(param.getSimpleName().toString(), param.asType(), constructor, i));
        }

        return accessors;
    }
}
