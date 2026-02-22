package io.github.joke.caffeinate.codegen.strategy;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.caffeinate.resolution.Converter;
import io.github.joke.caffeinate.resolution.ConverterRegistry;
import io.github.joke.caffeinate.resolution.MethodConverter;
import java.util.List;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

@AutoService(TypeMappingStrategy.class)
public class OptionalMappingStrategy implements TypeMappingStrategy {

    @Override
    public boolean canContribute(
            TypeMirror source, TypeMirror target, ConverterRegistry registry, ProcessingEnvironment env) {
        if (!isOptional(target)) return false;
        TypeMirror tgtElem = elementType(target);
        if (isOptional(source)) {
            TypeMirror srcElem = elementType(source);
            return env.getTypeUtils().isSameType(srcElem, tgtElem) || registry.hasConverter(srcElem, tgtElem);
        }
        // non-Optional source -> Optional<T> target
        return env.getTypeUtils().isSameType(source, tgtElem) || registry.hasConverter(source, tgtElem);
    }

    @Override
    public CodeBlock generate(
            String sourceExpr,
            TypeMirror source,
            TypeMirror target,
            ConverterRegistry registry,
            ProcessingEnvironment env) {
        TypeMirror tgtElem = elementType(target);
        if (isOptional(source)) {
            TypeMirror srcElem = elementType(source);
            if (env.getTypeUtils().isSameType(srcElem, tgtElem)) {
                return CodeBlock.of("$L", sourceExpr);
            }
            String methodName = methodName(registry.converterFor(srcElem, tgtElem));
            return CodeBlock.of("$L.map(this::$L)", sourceExpr, methodName);
        }
        // non-Optional source -> Optional<T> target
        if (env.getTypeUtils().isSameType(source, tgtElem)) {
            return CodeBlock.of("$T.ofNullable($L)", Optional.class, sourceExpr);
        }
        String methodName = methodName(registry.converterFor(source, tgtElem));
        return CodeBlock.of("$T.ofNullable(this.$L($L))", Optional.class, methodName, sourceExpr);
    }

    private String methodName(Optional<Converter> converter) {
        if (converter.isPresent() && converter.get() instanceof MethodConverter) {
            return ((MethodConverter) converter.get())
                    .getMethod()
                    .getSimpleName()
                    .toString();
        }
        throw new IllegalStateException("OptionalMappingStrategy: expected a MethodConverter");
    }

    private boolean isOptional(TypeMirror type) {
        if (!(type instanceof DeclaredType)) return false;
        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();
        return element.getQualifiedName().toString().equals("java.util.Optional")
                && !declared.getTypeArguments().isEmpty();
    }

    private TypeMirror elementType(TypeMirror type) {
        List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
        return args.get(0);
    }
}
