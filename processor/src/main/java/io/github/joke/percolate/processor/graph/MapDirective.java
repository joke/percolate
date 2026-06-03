package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.spi.Directive;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;

/**
 * Processor-side {@link Directive} over a {@code @Map} {@link AnnotationMirror}. It exposes the in-effect mapping
 * configuration to strategies without handing them the raw mirror: {@link #sourcePath()} splits the {@code source}
 * attribute into segments, and {@link #attribute(String)} reads any declared string attribute by name. Built once
 * by the driver from the seed edge's mirror (or a node's inherited directive) and carried on the {@link Frontier}.
 */
public final class MapDirective implements Directive {

    private static final String SOURCE_ATTRIBUTE = "source";

    private final AnnotationMirror mirror;

    private MapDirective(final AnnotationMirror mirror) {
        this.mirror = mirror;
    }

    public static MapDirective from(final AnnotationMirror mirror) {
        return new MapDirective(mirror);
    }

    @Override
    public List<String> sourcePath() {
        return attribute(SOURCE_ATTRIBUTE)
                .filter(source -> !source.isEmpty())
                .map(source -> List.of(source.split("\\.", -1)))
                .orElseGet(List::of);
    }

    @Override
    public Optional<String> attribute(final String name) {
        return mirror.getElementValues().entrySet().stream()
                .filter(entry -> entry.getKey().getSimpleName().contentEquals(name))
                .map(entry -> stringValueOf(entry.getValue()))
                .findFirst();
    }

    private static String stringValueOf(final AnnotationValue value) {
        final var raw = value.getValue();
        return raw == null ? "" : raw.toString();
    }
}
