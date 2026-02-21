package io.github.joke.caffeinate.analysis.property;

import com.google.auto.service.AutoService;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.List;

@AutoService(PropertyDiscoveryStrategy.class)
public class FieldPropertyStrategy implements PropertyDiscoveryStrategy {

    @Override
    public List<Property> discover(TypeElement type, ProcessingEnvironment env) {
        List<Property> properties = new ArrayList<>();
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) continue;
            VariableElement field = (VariableElement) enclosed;
            if (field.getModifiers().contains(Modifier.STATIC)) continue;
            properties.add(new Property(field.getSimpleName().toString(), field.asType(), field));
        }
        return properties;
    }
}
