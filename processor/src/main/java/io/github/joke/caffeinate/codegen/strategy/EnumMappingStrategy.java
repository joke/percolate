package io.github.joke.caffeinate.codegen.strategy;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.caffeinate.resolution.ConverterRegistry;
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
    public boolean canContribute(
            TypeMirror source, TypeMirror target, ConverterRegistry registry, ProcessingEnvironment env) {
        if (!isEnum(source) || !isEnum(target)) return false;
        TypeElement srcEnum = (TypeElement) ((DeclaredType) source).asElement();
        TypeElement tgtEnum = (TypeElement) ((DeclaredType) target).asElement();
        Set<String> tgtConstants = enumConstants(tgtEnum);
        for (String c : enumConstants(srcEnum)) {
            if (!tgtConstants.contains(c)) {
                env.getMessager()
                        .printMessage(
                                Diagnostic.Kind.ERROR,
                                String.format(
                                        "[Percolate] Enum constant '%s' from %s has no match in %s",
                                        c, srcEnum.getSimpleName(), tgtEnum.getSimpleName()));
                return false;
            }
        }
        return true;
    }

    @Override
    public CodeBlock generate(
            String sourceExpr,
            TypeMirror source,
            TypeMirror target,
            ConverterRegistry registry,
            ProcessingEnvironment env) {
        TypeElement targetEnum = (TypeElement) ((DeclaredType) target).asElement();
        return CodeBlock.of("$L.valueOf($L.name())", targetEnum.getQualifiedName(), sourceExpr);
    }

    private boolean isEnum(TypeMirror type) {
        if (!(type instanceof DeclaredType)) return false;
        return ((DeclaredType) type).asElement().getKind() == ElementKind.ENUM;
    }

    private Set<String> enumConstants(TypeElement enumType) {
        Set<String> result = new LinkedHashSet<>();
        for (Element e : enumType.getEnclosedElements()) {
            if (e.getKind() == ElementKind.ENUM_CONSTANT)
                result.add(e.getSimpleName().toString());
        }
        return result;
    }
}
