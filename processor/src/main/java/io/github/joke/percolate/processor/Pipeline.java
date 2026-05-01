package io.github.joke.percolate.processor;

import com.palantir.javapoet.JavaFile;
import jakarta.inject.Inject;
import javax.lang.model.element.TypeElement;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor(onConstructor_ = @Inject)
final class Pipeline {

    private final DiscoverAbstractMethods discoverAbstractMethods;
    private final DiscoverMappings discoverMappings;
    private final ValidateNoDuplicateTargets validateNoDuplicateTargets;

    @Nullable
    JavaFile process(final TypeElement element) {
        final var shape = discoverAbstractMethods.apply(element);
        final var mappings = discoverMappings.apply(shape);
        validateNoDuplicateTargets.validate(mappings);
        return null;
    }
}
