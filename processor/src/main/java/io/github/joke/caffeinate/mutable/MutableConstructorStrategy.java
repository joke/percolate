package io.github.joke.caffeinate.mutable;

import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import io.github.joke.caffeinate.strategy.ClassModel;
import io.github.joke.caffeinate.strategy.GenerationStrategy;
import io.github.joke.caffeinate.strategy.Property;
import javax.inject.Inject;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

public class MutableConstructorStrategy implements GenerationStrategy {

    @Inject
    MutableConstructorStrategy() {}

    @Override
    public void generate(TypeElement source, ClassModel model) {
        MethodSpec.Builder noArgs = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        if (source.getKind() != ElementKind.INTERFACE) {
            noArgs.addStatement("super()");
        }
        model.getMethods().add(noArgs.build());

        if (!model.getProperties().isEmpty()) {
            MethodSpec.Builder allArgs = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
            if (source.getKind() != ElementKind.INTERFACE) {
                allArgs.addStatement("super()");
            }

            for (Property property : model.getProperties()) {
                allArgs.addParameter(ParameterSpec.builder(property.getType(), property.getFieldName())
                        .build());
                allArgs.addStatement("this.$N = $N", property.getFieldName(), property.getFieldName());
            }

            model.getMethods().add(allArgs.build());
        }
    }
}
