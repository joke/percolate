package io.github.joke.caffeinate.codegen.strategy;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

@AutoService(TypeMappingStrategy.class)
public class OptionalMappingStrategy implements TypeMappingStrategy {

    @Override
    public boolean supports(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return isOptional(source) && isOptional(target);
    }

    @Override
    public CodeBlock generate(
            String sourceExpr,
            TypeMirror source,
            TypeMirror target,
            String converterMethodRef,
            ProcessingEnvironment env) {
        return CodeBlock.of("$L.map(this::$L)", sourceExpr, converterMethodRef);
    }

    private boolean isOptional(TypeMirror type) {
        if (!(type instanceof DeclaredType)) return false;
        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();
        return element.getQualifiedName().toString().equals("java.util.Optional")
                && !declared.getTypeArguments().isEmpty();
    }
}
