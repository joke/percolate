package io.github.joke.percolate.processor.transform;

import com.palantir.javapoet.CodeBlock;

@FunctionalInterface
public interface CodeTemplate {

    CodeBlock apply(CodeBlock innerExpression);
}
