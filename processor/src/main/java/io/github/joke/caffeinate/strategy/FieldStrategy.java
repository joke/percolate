package io.github.joke.caffeinate.strategy;

import com.palantir.javapoet.FieldSpec;
import javax.inject.Inject;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

public class FieldStrategy implements GenerationStrategy {

    @Inject
    FieldStrategy() {}

    @Override
    public void generate(TypeElement source, ClassModel model) {
        for (Property property : model.getProperties()) {
            FieldSpec.Builder field = FieldSpec.builder(
                            property.getType(), property.getFieldName(), Modifier.PRIVATE, Modifier.FINAL);
            property.getAnnotations().forEach(field::addAnnotation);
            model.getFields().add(field.build());
        }
    }
}
