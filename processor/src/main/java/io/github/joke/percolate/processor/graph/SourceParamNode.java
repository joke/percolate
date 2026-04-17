package io.github.joke.percolate.processor.graph;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Root node representing a mapper-method source parameter.
 *
 * <p>One {@code SourceParamNode} is created per method parameter by {@code BuildValueGraphStage}.
 */
@Getter
@ToString
@EqualsAndHashCode(callSuper = false)
@RequiredArgsConstructor
public final class SourceParamNode extends ValueNode {

    private final VariableElement element;
    private final TypeMirror type;

    public String getName() {
        return element.getSimpleName().toString();
    }
}
