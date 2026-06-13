package io.github.joke.percolate.processor.graph;

import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import lombok.Value;

@Value
public class MethodScope implements Scope {
    ExecutableElement method;

    @Override
    public String encode() {
        final var name = method.getSimpleName().toString();
        final var paramTypes = method.getParameters().stream()
                .map(p -> p.asType().toString())
                .collect(java.util.stream.Collectors.joining(","));
        return name + "(" + paramTypes + ")";
    }

    @Override
    public Optional<Scope> parent() {
        return Optional.of(MapperScope.INSTANCE);
    }
}
