package io.github.joke.caffeinate.strategy;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.TypeName;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;

public final class PropertyUtils {

    private PropertyUtils() {}

    public static boolean isGetterMethod(ExecutableElement method) {
        if (!method.getParameters().isEmpty()) {
            return false;
        }
        if (method.getReturnType().getKind() == TypeKind.VOID) {
            return false;
        }
        String name = method.getSimpleName().toString();
        return (name.startsWith("get") && name.length() > 3) || (name.startsWith("is") && name.length() > 2);
    }

    public static boolean isSetterMethod(ExecutableElement method) {
        String name = method.getSimpleName().toString();
        return method.getParameters().size() == 1
                && method.getReturnType().getKind() == TypeKind.VOID
                && name.startsWith("set")
                && name.length() > 3;
    }

    public static Property extractProperty(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        String fieldName;

        if (methodName.startsWith("get") && methodName.length() > 3) {
            fieldName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            fieldName = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        } else {
            throw new IllegalArgumentException("Not a getter method: " + methodName);
        }

        TypeName type = TypeName.get(method.getReturnType());

        List<AnnotationSpec> annotations = method.getAnnotationMirrors().stream()
                .filter(m -> m.getAnnotationType().asElement().getSimpleName().contentEquals("Nullable"))
                .map(AnnotationSpec::get)
                .collect(Collectors.toList());

        return new Property(fieldName, type, methodName, annotations);
    }

    public static String setterNameForField(String fieldName) {
        return "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }
}
