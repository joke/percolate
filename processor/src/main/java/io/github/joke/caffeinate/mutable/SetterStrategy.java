package io.github.joke.caffeinate.mutable;

import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import io.github.joke.caffeinate.strategy.ClassModel;
import io.github.joke.caffeinate.strategy.GenerationStrategy;
import io.github.joke.caffeinate.strategy.Property;
import io.github.joke.caffeinate.strategy.PropertyUtils;
import javax.inject.Inject;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

public class SetterStrategy implements GenerationStrategy {

    @Inject
    SetterStrategy() {}

    @Override
    public void generate(TypeElement source, ClassModel model) {
        for (Property property : model.getProperties()) {
            ParameterSpec.Builder param = ParameterSpec.builder(property.getType(), property.getFieldName());
            property.getAnnotations().forEach(param::addAnnotation);
            MethodSpec setter = MethodSpec.methodBuilder(PropertyUtils.setterNameForField(property.getFieldName()))
                    .addModifiers(Modifier.PUBLIC)
                    .returns(void.class)
                    .addParameter(param.build())
                    .addStatement("this.$N = $N", property.getFieldName(), property.getFieldName())
                    .build();
            model.getMethods().add(setter);
        }
    }
}
