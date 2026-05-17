package io.github.joke.percolate.processor;

import com.google.auto.common.BasicAnnotationProcessor.Step;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import io.github.joke.percolate.Mapper;
import jakarta.inject.Inject;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
final class MapperStep implements Step {

    private static final String MAPPER_FQN = Mapper.class.getCanonicalName();

    private final Pipeline pipeline;
    private final Diagnostics diagnostics;

    @Override
    public Set<String> annotations() {
        return ImmutableSet.of(MAPPER_FQN);
    }

    @Override
    public Set<? extends Element> process(final ImmutableSetMultimap<String, Element> elementsByAnnotation) {
        diagnostics.reset();
        final var mapperElements = elementsByAnnotation.get(MAPPER_FQN);
        mapperElements.stream()
                .filter(TypeElement.class::isInstance)
                .map(TypeElement.class::cast)
                .forEach(pipeline::process);
        return ImmutableSet.of();
    }
}
