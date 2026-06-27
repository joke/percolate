package io.github.joke.percolate.processor.internal.stages.generate;

import com.palantir.javapoet.CodeBlock;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import lombok.Value;

@Value
final class MethodImpl {
    ExecutableElement method;
    CodeBlock body;
    Set<TypeElement> requiredMapperDeps;
}
