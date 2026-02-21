package io.github.joke.caffeinate.codegen.strategy;

import com.palantir.javapoet.CodeBlock;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;

public interface TypeMappingStrategy {
    boolean supports(TypeMirror source, TypeMirror target, ProcessingEnvironment env);
    CodeBlock generate(String sourceExpr, TypeMirror source, TypeMirror target,
                       String converterMethodRef, ProcessingEnvironment env);
}
