package io.github.joke.percolate.processor.model;

import io.github.joke.percolate.processor.transform.CodeTemplate;
import javax.lang.model.type.TypeMirror;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public abstract class ReadAccessor {

    private final String name;
    private final TypeMirror type;

    /**
     * The {@link CodeTemplate} rendering a read of this property, given the upstream expression
     * representing the owning instance. Used by {@code BuildValueGraphStage} to populate the
     * {@code CodeTemplate} on each {@code PropertyReadEdge} at construction time.
     */
    public abstract CodeTemplate template();
}
