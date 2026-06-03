package io.github.joke.percolate.spi;

import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.type.TypeMirror;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * One input of an {@link ExpansionStep}: a named, typed binding point with a weight and, when it originates from a
 * declared program element (a constructor/method parameter), the {@link AnnotatedConstruct} it was produced from
 * (used to derive the consumer nullability contract). Synthetic slots with no originating construct (e.g. a
 * container element) carry a {@code null} {@link #producedFrom}.
 */
@Value
public class Slot {
    String name;
    TypeMirror type;
    int weight;

    @Nullable
    AnnotatedConstruct producedFrom;
}
