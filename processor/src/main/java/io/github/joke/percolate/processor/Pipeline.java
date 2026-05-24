package io.github.joke.percolate.processor;

import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import javax.lang.model.element.TypeElement;
import java.util.List;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
final class Pipeline {

    private final List<Stage> stages;

    Void process(final TypeElement element) {
        final var ctx = new MapperContext(element);
        ProcessorModule.setCurrentContext(ctx);
        try {
            stages.forEach(s -> s.run(ctx));
        } finally {
            ProcessorModule.clearCurrentContext();
        }
        return null;
    }
}
