package io.github.joke.percolate.processor.graph;

import com.palantir.javapoet.CodeBlock;
import java.util.Map;
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

    @Override
    public CodeBlock compose(final Map<ValueEdge, CodeBlock> inputs, final ComposeKind kind) {
        if (kind != ComposeKind.EXPRESSION) {
            throw new IllegalStateException("SourceParamNode supports EXPRESSION only, got: " + kind);
        }
        if (!inputs.isEmpty()) {
            throw new IllegalStateException(
                    "SourceParamNode has no incoming edges; inputs must be empty, got: " + inputs.size());
        }
        return CodeBlock.of("$L", getName());
    }
}
