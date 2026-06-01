package io.github.joke.percolate.spi;

import java.util.List;
import java.util.Optional;

/**
 * The {@code @Map} configuration in effect at a {@link Frontier}, exposed to strategies without raw compiler
 * internals. A strategy reads its per-binding configuration here — the source path it descends, and any declared
 * attribute (e.g. a future conversion pattern or default value) — rather than inspecting an {@code AnnotationMirror}.
 */
public interface Directive {

    /** The {@code @Map} source path split into segments, e.g. {@code ["person", "address", "street"]}. */
    List<String> sourcePath();

    /** A declared {@code @Map} string attribute by name (e.g. a conversion pattern), or empty when absent. */
    Optional<String> attribute(String name);
}
