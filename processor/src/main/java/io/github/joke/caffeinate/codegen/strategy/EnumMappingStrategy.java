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

@AutoService(TypeMappingStrategy.class)
public class EnumMappingStrategy implements TypeMappingStrategy {

    @Override
    public boolean supports(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return isEnum(source) && isEnum(target);
    }

    @Override
    public CodeBlock generate(
            String sourceExpr,
            TypeMirror source,
            TypeMirror target,
            String converterMethodRef,
            ProcessingEnvironment env) {
        TypeElement targetEnum = (TypeElement) ((DeclaredType) target).asElement();
        TypeElement sourceEnum = (TypeElement) ((DeclaredType) source).asElement();

        Set<String> targetConstants = enumConstants(targetEnum);
        Set<String> sourceConstants = enumConstants(sourceEnum);

        // Validate all source constants exist in target
        for (String constant : sourceConstants) {
            if (!targetConstants.contains(constant)) {
                env.getMessager()
                        .printMessage(
                                Diagnostic.Kind.ERROR,
                                "[Percolate] Enum constant '" + constant + "' from "
                                        + sourceEnum.getSimpleName() + " has no match in "
                                        + targetEnum.getSimpleName());
            }
        }

        // Use valueOf(name()) pattern — maps by name at runtime
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
