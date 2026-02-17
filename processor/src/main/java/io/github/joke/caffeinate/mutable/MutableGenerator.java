package io.github.joke.caffeinate.mutable;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.joke.caffeinate.phase.AnalysisPhase;
import io.github.joke.caffeinate.phase.GenerationPhase;
import io.github.joke.caffeinate.phase.ValidationPhase;
import io.github.joke.caffeinate.strategy.ClassModel;
import io.github.joke.caffeinate.strategy.GenerationStrategy;
import java.io.IOException;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

public class MutableGenerator {

    private final Set<GenerationStrategy> analysisStrategies;
    private final Set<GenerationStrategy> validationStrategies;
    private final Set<GenerationStrategy> generationStrategies;
    private final Filer filer;

    @Inject
    MutableGenerator(
            @AnalysisPhase Set<GenerationStrategy> analysisStrategies,
            @ValidationPhase Set<GenerationStrategy> validationStrategies,
            @GenerationPhase Set<GenerationStrategy> generationStrategies,
            Filer filer) {
        this.analysisStrategies = analysisStrategies;
        this.validationStrategies = validationStrategies;
        this.generationStrategies = generationStrategies;
        this.filer = filer;
    }

    public void generate(TypeElement source) throws IOException {
        ClassModel model = new ClassModel();

        for (GenerationStrategy strategy : analysisStrategies) {
            strategy.generate(source, model);
        }

        for (GenerationStrategy strategy : validationStrategies) {
            strategy.generate(source, model);
        }

        if (model.hasErrors()) {
            return;
        }

        for (GenerationStrategy strategy : generationStrategies) {
            strategy.generate(source, model);
        }

        TypeSpec.Builder builder = TypeSpec.classBuilder(model.getClassName());
        for (Modifier modifier : model.getModifiers()) {
            builder.addModifiers(modifier);
        }
        for (TypeName superinterface : model.getSuperinterfaces()) {
            builder.addSuperinterface(superinterface);
        }
        for (FieldSpec field : model.getFields()) {
            builder.addField(field);
        }
        for (MethodSpec method : model.getMethods()) {
            builder.addMethod(method);
        }
        TypeSpec typeSpec = builder.build();

        ClassName sourceClass = ClassName.get(source);
        JavaFile javaFile =
                JavaFile.builder(sourceClass.packageName(), typeSpec).build();
        javaFile.writeTo(filer);
    }
}
