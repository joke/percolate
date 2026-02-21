package io.github.joke.caffeinate.analysis.property;

import com.google.auto.service.AutoService;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import java.util.ArrayList;
import java.util.List;

@AutoService(PropertyDiscoveryStrategy.class)
public class GetterPropertyStrategy implements PropertyDiscoveryStrategy {

    @Override
    public List<Property> discover(TypeElement type, ProcessingEnvironment env) {
        List<Property> properties = new ArrayList<>();
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) continue;
            ExecutableElement method = (ExecutableElement) enclosed;
            if (!method.getParameters().isEmpty()) continue;
            if (method.getReturnType().getKind() == TypeKind.VOID) continue;
            String methodName = method.getSimpleName().toString();
            String propertyName = extractPropertyName(methodName, method);
            if (propertyName == null) continue;
            properties.add(new Property(propertyName, method.getReturnType(), method));
        }
        return properties;
    }

    private @Nullable String extractPropertyName(String methodName, ExecutableElement method) {
        if (methodName.startsWith("get") && methodName.length() > 3
                && !methodName.equals("getClass")) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        if (methodName.startsWith("is") && methodName.length() > 2
                && method.getReturnType().getKind() == TypeKind.BOOLEAN) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        return null;
    }
}
