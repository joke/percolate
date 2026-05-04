package io.github.joke.percolate.processor;

import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.model.MapperMappings;
import io.github.joke.percolate.processor.model.MapperShape;
import jakarta.inject.Inject;
import javax.lang.model.element.TypeElement;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Getter
@Setter
public final class MapperContext {
    private final TypeElement mapperType;
    private @Nullable MapperShape shape;
    private @Nullable MapperMappings mappings;
    private @Nullable MapperGraph graph;

    public boolean isScarred(Diagnostics diagnostics) {
        return diagnostics.hasErrorsFor(mapperType);
    }
}
