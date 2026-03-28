package io.github.joke.percolate.processor.spi;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toUnmodifiableList;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.processor.model.ConstructorParamAccessor;
import io.github.joke.percolate.processor.model.WriteAccessor;
import java.util.List;
import java.util.stream.IntStream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

@AutoService(TargetPropertyDiscovery.class)
public final class ConstructorDiscovery implements TargetPropertyDiscovery {

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public List<WriteAccessor> discover(final TypeMirror type, final Elements elements, final Types types) {
        if (!(type instanceof DeclaredType)) {
            return List.of();
        }

        final TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();

        final ExecutableElement constructor = typeElement.getEnclosedElements().stream()
                .filter(e -> e.getKind() == CONSTRUCTOR)
                .map(ExecutableElement.class::cast)
                .max(comparingInt(c -> c.getParameters().size()))
                .orElse(null);

        if (constructor == null) {
            return List.of();
        }

        final List<? extends VariableElement> params = constructor.getParameters();
        return IntStream.range(0, params.size())
                .mapToObj(i -> {
                    final VariableElement param = params.get(i);
                    return (WriteAccessor) new ConstructorParamAccessor(
                            param.getSimpleName().toString(), param.asType(), constructor, i);
                })
                .collect(toUnmodifiableList());
    }
}
