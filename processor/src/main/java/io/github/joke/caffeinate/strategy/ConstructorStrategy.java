package io.github.joke.caffeinate.strategy;

import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import javax.inject.Inject;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

public class ConstructorStrategy implements GenerationStrategy {

    @Inject
    ConstructorStrategy() {}

    @Override
    public void generate(TypeElement source, ClassModel model) {
        if (model.getProperties().isEmpty()) {
            return;
        }

        MethodSpec.Builder constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

        if (source.getKind() != ElementKind.INTERFACE) {
            constructor.addStatement("super()");
        }

        for (Property property : model.getProperties()) {
            constructor.addParameter(ParameterSpec.builder(property.getType(), property.getFieldName())
                    .build());
            constructor.addStatement("this.$N = $N", property.getFieldName(), property.getFieldName());
        }

        model.getMethods().add(constructor.build());
    }
}
