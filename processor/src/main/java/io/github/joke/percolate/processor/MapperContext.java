package io.github.joke.percolate.processor;

import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.model.MapperMappings;
import io.github.joke.percolate.processor.model.MapperShape;
import io.github.joke.percolate.processor.spi.CallableMethods;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Getter
@Setter
public final class MapperContext {
    private final TypeElement mapperType;
    private @Nullable MapperShape shape;
    private @Nullable MapperMappings mappings;
    private @Nullable MapperGraph graph;
    private @Nullable CallableMethods callableMethods;
    private @Nullable ExecutableElement currentMethod;

    public boolean isScarred(final Diagnostics diagnostics) {
        return diagnostics.hasErrorsFor(mapperType);
    }
}
