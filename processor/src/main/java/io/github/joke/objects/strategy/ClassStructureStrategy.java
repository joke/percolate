package io.github.joke.objects.strategy;

import com.palantir.javapoet.ClassName;

import javax.inject.Inject;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

public class ClassStructureStrategy implements GenerationStrategy {

    @Inject
    ClassStructureStrategy() {}

    @Override
    public void generate(TypeElement source, ClassModel model) {
        model.setClassName(source.getSimpleName() + "Impl");
        model.getModifiers().add(Modifier.PUBLIC);
        model.getSuperinterfaces().add(ClassName.get(source));
    }
}
