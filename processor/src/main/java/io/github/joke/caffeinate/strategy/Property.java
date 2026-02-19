package io.github.joke.caffeinate.strategy;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.TypeName;
import java.util.List;

public class Property {

    private final String fieldName;
    private final TypeName type;
    private final String getterName;
    private final List<AnnotationSpec> annotations;

    public Property(String fieldName, TypeName type, String getterName, List<AnnotationSpec> annotations) {
        this.fieldName = fieldName;
        this.type = type;
        this.getterName = getterName;
        this.annotations = List.copyOf(annotations);
    }

    public String getFieldName() {
        return fieldName;
    }

    public TypeName getType() {
        return type;
    }

    public String getGetterName() {
        return getterName;
    }

    public List<AnnotationSpec> getAnnotations() {
        return annotations;
    }
}
