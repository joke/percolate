package io.github.joke.caffeinate.codegen.strategy;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.caffeinate.resolution.Converter;
import io.github.joke.caffeinate.resolution.ConverterRegistry;
import io.github.joke.caffeinate.resolution.MethodConverter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

@AutoService(TypeMappingStrategy.class)
public class CollectionMappingStrategy implements TypeMappingStrategy {

    @Override
    public boolean canContribute(
            TypeMirror source, TypeMirror target, ConverterRegistry registry, ProcessingEnvironment env) {
        if (!isList(source) || !isList(target)) return false;
        TypeMirror srcElem = elementType(source);
        TypeMirror tgtElem = elementType(target);
        return env.getTypeUtils().isSameType(srcElem, tgtElem) || registry.hasConverter(srcElem, tgtElem);
    }

    @Override
    public CodeBlock generate(
            String sourceExpr,
            TypeMirror source,
            TypeMirror target,
            ConverterRegistry registry,
            ProcessingEnvironment env) {
        TypeMirror srcElem = elementType(source);
        TypeMirror tgtElem = elementType(target);
        if (env.getTypeUtils().isSameType(srcElem, tgtElem)) {
            return CodeBlock.of("$L", sourceExpr);
        }
        Optional<Converter> converter = registry.converterFor(srcElem, tgtElem);
        if (converter.isPresent() && converter.get() instanceof MethodConverter) {
            String methodName = ((MethodConverter) converter.get())
                    .getMethod()
                    .getSimpleName()
                    .toString();
            return CodeBlock.of(
                    "$L.stream().map(this::$L).collect($T.toList())", sourceExpr, methodName, Collectors.class);
        }
        return CodeBlock.of("$L /* unresolved collection mapping */", sourceExpr);
    }

    private boolean isList(TypeMirror type) {
        if (!(type instanceof DeclaredType)) return false;
        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();
        return element.getQualifiedName().toString().equals("java.util.List")
                && !declared.getTypeArguments().isEmpty();
    }

    private TypeMirror elementType(TypeMirror type) {
        List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
        return args.get(0);
    }
}
