package io.github.joke.caffeinate.analysis;

import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnalysisStage {

    @Inject
    public AnalysisStage() {
    }

    public AnalysisResult analyze(Set<? extends Element> mapperElements) {
        List<MapperDescriptor> descriptors = new ArrayList<>();
        for (Element element : mapperElements) {
            if (element.getKind() != ElementKind.INTERFACE) continue;
            TypeElement mapperInterface = (TypeElement) element;
            descriptors.add(analyzeMapper(mapperInterface));
        }
        return new AnalysisResult(descriptors);
    }

    private MapperDescriptor analyzeMapper(TypeElement mapperInterface) {
        List<ExecutableElement> allMethods = allMethods(mapperInterface);
        List<ExecutableElement> converterCandidates = new ArrayList<>(allMethods);
        List<MappingMethod> mappingMethods = new ArrayList<>();

        for (ExecutableElement method : allMethods) {
            if (!isAbstract(method)) continue;
            TypeElement targetType = resolveTypeElement(method.getReturnType());
            if (targetType == null) continue;
            List<MapAnnotation> mapAnnotations = extractMapAnnotations(method);
            mappingMethods.add(new MappingMethod(
                    method, targetType, method.getParameters(), mapAnnotations, converterCandidates));
        }
        return new MapperDescriptor(mapperInterface, mappingMethods);
    }

    private List<ExecutableElement> allMethods(TypeElement iface) {
        List<ExecutableElement> methods = new ArrayList<>();
        for (Element enclosed : iface.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                methods.add((ExecutableElement) enclosed);
            }
        }
        return methods;
    }

    private boolean isAbstract(ExecutableElement method) {
        return method.getModifiers().contains(Modifier.ABSTRACT);
    }

    private @Nullable TypeElement resolveTypeElement(TypeMirror type) {
        if (!(type instanceof DeclaredType)) return null;
        Element element = ((DeclaredType) type).asElement();
        return element instanceof TypeElement ? (TypeElement) element : null;
    }

    private List<MapAnnotation> extractMapAnnotations(ExecutableElement method) {
        List<MapAnnotation> annotations = new ArrayList<>();
        for (AnnotationMirror mirror : method.getAnnotationMirrors()) {
            String annotationName = ((TypeElement) mirror.getAnnotationType().asElement())
                    .getQualifiedName().toString();
            if (annotationName.equals("io.github.joke.caffeinate.Map")) {
                annotations.add(parseMapAnnotation(mirror));
            } else if (annotationName.equals("io.github.joke.caffeinate.MapList")) {
                annotations.addAll(parseMapListAnnotation(mirror));
            }
        }
        return annotations;
    }

    private MapAnnotation parseMapAnnotation(AnnotationMirror mirror) {
        String target = "";
        String source = "";
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : mirror.getElementValues().entrySet()) {
            String name = entry.getKey().getSimpleName().toString();
            if (name.equals("target")) target = (String) entry.getValue().getValue();
            else if (name.equals("source")) source = (String) entry.getValue().getValue();
        }
        return new MapAnnotation(target, source);
    }

    private List<MapAnnotation> parseMapListAnnotation(AnnotationMirror mirror) {
        List<MapAnnotation> result = new ArrayList<>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : mirror.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().toString().equals("value")) {
                @SuppressWarnings("unchecked")
                List<? extends AnnotationValue> values =
                        (List<? extends AnnotationValue>) entry.getValue().getValue();
                for (AnnotationValue av : values) {
                    result.add(parseMapAnnotation((AnnotationMirror) av.getValue()));
                }
            }
        }
        return result;
    }
}
