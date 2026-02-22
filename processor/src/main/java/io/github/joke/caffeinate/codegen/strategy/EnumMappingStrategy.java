package io.github.joke.caffeinate.codegen.strategy;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.jspecify.annotations.Nullable;

@AutoService(TypeMappingStrategy.class)
public class EnumMappingStrategy implements TypeMappingStrategy {

    @Override
    public boolean supports(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return isEnum(source) && isEnum(target);
    }

    /** Enum-to-enum mapping does not require a converter method — always supported. */
    @Override
    public boolean supportsIdentity(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return true;
    }

    @Override
    public CodeBlock generate(
            String sourceExpr,
            TypeMirror source,
            TypeMirror target,
            @Nullable String converterMethodRef,
            ProcessingEnvironment env) {
        if (!(target instanceof DeclaredType) || !(source instanceof DeclaredType)) {
            throw new IllegalStateException("EnumMappingStrategy.generate() called on non-DeclaredType");
        }
        Element targetEl = ((DeclaredType) target).asElement();
        Element sourceEl = ((DeclaredType) source).asElement();
        if (!(targetEl instanceof TypeElement) || !(sourceEl instanceof TypeElement)) {
            throw new IllegalStateException("EnumMappingStrategy.generate() called on non-TypeElement");
        }
        TypeElement targetEnum = (TypeElement) targetEl;
        TypeElement sourceEnum = (TypeElement) sourceEl;

        Set<String> targetConstants = enumConstants(targetEnum);
        Set<String> sourceConstants = enumConstants(sourceEnum);

        // Validate all source constants exist in target
        boolean hasErrors = false;
        for (String constant : sourceConstants) {
            if (!targetConstants.contains(constant)) {
                env.getMessager()
                        .printMessage(
                                Diagnostic.Kind.ERROR,
                                String.format(
                                        "[Percolate] Enum constant '%s' from %s has no match in %s",
                                        constant, sourceEnum.getSimpleName(), targetEnum.getSimpleName()));
                hasErrors = true;
            }
        }
        if (hasErrors) {
            return CodeBlock.of(
                    "throw new $T(\"[Percolate] Enum mapping error for: \" + $L)",
                    IllegalStateException.class,
                    sourceExpr);
        }

        // Only reach here if all constants matched — use valueOf(name()) pattern
        String targetFqn = targetEnum.getQualifiedName().toString();
        return CodeBlock.of("$L.valueOf($L.name())", targetFqn, sourceExpr);
    }

    private boolean isEnum(TypeMirror type) {
        if (!(type instanceof DeclaredType)) return false;
        return ((DeclaredType) type).asElement().getKind() == ElementKind.ENUM;
    }

    private Set<String> enumConstants(TypeElement enumType) {
        Set<String> result = new LinkedHashSet<>();
        for (Element e : enumType.getEnclosedElements()) {
            if (e.getKind() == ElementKind.ENUM_CONSTANT) {
                result.add(e.getSimpleName().toString());
            }
        }
        return result;
    }
}
