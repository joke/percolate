package io.github.joke.percolate.processor.internal.stages.generate;

import io.github.joke.percolate.javapoet.AnnotationSpec;
import io.github.joke.percolate.javapoet.ClassName;
import io.github.joke.percolate.javapoet.JavaFile;
import io.github.joke.percolate.javapoet.MethodSpec;
import io.github.joke.percolate.javapoet.ParameterSpec;
import io.github.joke.percolate.javapoet.TypeName;
import io.github.joke.percolate.javapoet.TypeSpec;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.ProcessorOptions;
import jakarta.inject.Inject;
import java.io.IOException;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Generated;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class AssembleMapperType {

    private static final String GENERATED_VALUE = "io.github.joke.percolate";

    private final Filer filer;
    private final Elements elements;
    private final ProcessorOptions options;

    void assemble(final MapperContext ctx, final MethodBodies methodBodies) throws IOException {
        final var mapperType = ctx.getMapperType();
        final var simpleName = mapperType.getSimpleName() + "Impl";
        final var packageName =
                elements.getPackageOf(mapperType).getQualifiedName().toString();

        final var typeBuilder = TypeSpec.classBuilder(simpleName)
                .addModifiers(classModifiers())
                .addAnnotation(generatedAnnotation())
                .addFields(methodBodies.getMembers())
                .addMethod(emptyPublicConstructor());

        if (mapperType.getKind() == ElementKind.INTERFACE) {
            typeBuilder.addSuperinterface(ClassName.get(mapperType));
        } else {
            typeBuilder.superclass(TypeName.get(mapperType.asType()));
        }

        methodBodies.getBodies().forEach(body -> typeBuilder.addMethod(overrideMethod(body)));

        JavaFile.builder(packageName, typeBuilder.build()).build().writeTo(filer);
    }

    Modifier[] classModifiers() {
        return options.isClassesFinal()
                ? new Modifier[] {Modifier.PUBLIC, Modifier.FINAL}
                : new Modifier[] {Modifier.PUBLIC};
    }

    static AnnotationSpec generatedAnnotation() {
        return AnnotationSpec.builder(Generated.class)
                .addMember("value", "$S", GENERATED_VALUE)
                .build();
    }

    static MethodSpec emptyPublicConstructor() {
        return MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).build();
    }

    MethodSpec overrideMethod(final MethodImpl impl) {
        final ExecutableElement method = impl.getMethod();
        final var builder = MethodSpec.methodBuilder(method.getSimpleName().toString())
                .addModifiers(methodModifiers())
                .addAnnotation(Override.class)
                .returns(returnTypeName(method))
                .addCode(impl.getBody());
        method.getThrownTypes().forEach(t -> builder.addException(TypeName.get(t)));
        method.getParameters().forEach(p -> builder.addParameter(parameterSpec(p)));
        return builder.build();
    }

    Modifier[] methodModifiers() {
        return options.isMethodsFinal()
                ? new Modifier[] {Modifier.PUBLIC, Modifier.FINAL}
                : new Modifier[] {Modifier.PUBLIC};
    }

    ParameterSpec parameterSpec(final VariableElement parameter) {
        final var builder = ParameterSpec.builder(
                TypeName.get(parameter.asType()), parameter.getSimpleName().toString());
        if (options.isParametersFinal()) {
            builder.addModifiers(Modifier.FINAL);
        }
        return builder.build();
    }

    static TypeName returnTypeName(final ExecutableElement method) {
        final var returnType = method.getReturnType();
        return returnType.getKind() == TypeKind.VOID ? TypeName.VOID : TypeName.get(returnType);
    }
}
