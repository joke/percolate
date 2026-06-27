package io.github.joke.percolate.processor;

import io.github.joke.percolate.processor.internal.stages.Stage;
import jakarta.inject.Inject;
import java.util.List;
import javax.lang.model.element.TypeElement;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
final class Pipeline {

    private final List<Stage> stages;

    MapperContext process(final TypeElement element) {
        final var ctx = new MapperContext(element);
        stages.forEach(s -> s.run(ctx));
        return ctx;
    }
}
