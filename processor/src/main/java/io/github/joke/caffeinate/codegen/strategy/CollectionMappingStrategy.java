package io.github.joke.caffeinate.codegen.strategy;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

@AutoService(TypeMappingStrategy.class)
public class CollectionMappingStrategy implements TypeMappingStrategy {

    @Override
    public boolean supports(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return isList(source) && isList(target);
    }

    /**
     * Supports identity (no converter needed) when the list element types are the same.
     */
    @Override
    public boolean supportsIdentity(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        TypeMirror srcElem = elementType(source);
        TypeMirror tgtElem = elementType(target);
        return env.getTypeUtils().isSameType(srcElem, tgtElem);
    }

    @Override
    public CodeBlock generate(
            String sourceExpr,
            TypeMirror source,
            TypeMirror target,
            @Nullable String converterMethodRef,
            ProcessingEnvironment env) {
        if (converterMethodRef == null) {
            // Identity: element types are the same — return source directly.
            return CodeBlock.of("$L", sourceExpr);
        }
        return CodeBlock.of(
                "$L.stream().map(this::$L).collect($T.toList())", sourceExpr, converterMethodRef, Collectors.class);
    }

    private boolean isList(TypeMirror type) {
        if (!(type instanceof DeclaredType)) return false;
        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();
        return element.getQualifiedName().toString().equals("java.util.List")
                && !declared.getTypeArguments().isEmpty();
    }

    /** Returns the first type argument of a generic declared type, or the type itself. */
    private TypeMirror elementType(TypeMirror type) {
        if (type instanceof DeclaredType) {
            java.util.List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
            if (!args.isEmpty()) {
                return args.get(0);
            }
        }
        return type;
    }
}
