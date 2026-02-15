package io.github.joke.objects.immutable;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.joke.objects.strategy.ClassModel;
import io.github.joke.objects.strategy.GenerationStrategy;

import java.io.IOException;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

public class ImmutableGenerator {

    private final Set<GenerationStrategy> strategies;
    private final Filer filer;

    @Inject
    ImmutableGenerator(Set<GenerationStrategy> strategies, Filer filer) {
        this.strategies = strategies;
        this.filer = filer;
    }

    public void generate(TypeElement source) throws IOException {
        ClassModel model = new ClassModel();
        for (GenerationStrategy strategy : strategies) {
            strategy.generate(source, model);
        }

        TypeSpec.Builder builder = TypeSpec.classBuilder(model.getClassName());
        for (Modifier modifier : model.getModifiers()) {
            builder.addModifiers(modifier);
        }
        for (TypeName superinterface : model.getSuperinterfaces()) {
            builder.addSuperinterface(superinterface);
        }
        TypeSpec typeSpec = builder.build();

        ClassName sourceClass = ClassName.get(source);
        JavaFile javaFile = JavaFile.builder(sourceClass.packageName(), typeSpec).build();
        javaFile.writeTo(filer);
    }
}
