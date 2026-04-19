package io.github.joke.percolate.processor.model;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.transform.CodeTemplate;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import lombok.Getter;

@Getter
public final class GetterAccessor extends ReadAccessor {

    private final ExecutableElement method;

    public GetterAccessor(final String name, final TypeMirror type, final ExecutableElement method) {
        super(name, type);
        this.method = method;
    }

    @Override
    public CodeTemplate template() {
        final var methodName = method.getSimpleName().toString();
        return input -> CodeBlock.of("$L.$N()", input, methodName);
    }
}
