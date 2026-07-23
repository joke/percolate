package io.github.joke.percolate.processor.internal.stages.generate;

import com.groupcdg.pitest.annotations.CoverageIgnore;
import io.github.joke.percolate.lib.javapoet.AnnotationSpec;
import io.github.joke.percolate.lib.javapoet.ClassName;
import io.github.joke.percolate.lib.javapoet.JavaFile;
import io.github.joke.percolate.lib.javapoet.MethodSpec;
import io.github.joke.percolate.lib.javapoet.ParameterSpec;
import io.github.joke.percolate.lib.javapoet.TypeName;
import io.github.joke.percolate.lib.javapoet.TypeSpec;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.ProcessorOptions;
import jakarta.inject.Inject;
import java.io.IOException;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Generated;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import lombok.RequiredArgsConstructor;

/**
 * Assembles and writes the generated mapper implementation via JavaPoet and the {@link Filer}. The thin
 * {@code javax.lang.model}/{@code Filer} leaf of code generation: covered end-to-end by the compile-based
 * feature-e2e layer, not by a unit-test javac substrate — its decision logic lives in {@link MapperTypeDecisions},
 * which is unit-tested directly.
 */
@CoverageIgnore
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class AssembleMapperType {

    private static final String GENERATED_VALUE = "io.github.joke.percolate";

    private final Filer filer;
    private final Elements elements;
    private final ProcessorOptions options;
    private final MapperTypeDecisions decisions;

    void assemble(final MapperContext ctx, final MethodBodies methodBodies) throws IOException {
        final var mapperType = ctx.getMapperType();
        final var simpleName = mapperType.getSimpleName() + "Impl";
        final var packageName =
                elements.getPackageOf(mapperType).getQualifiedName().toString();

        final var typeBuilder = TypeSpec.classBuilder(simpleName)
                .addModifiers(decisions.publicModifiers(options.isClassesFinal()))
                .addAnnotation(generatedAnnotation())
                .addFields(methodBodies.getMembers())
                .addMethod(emptyPublicConstructor());

        if (decisions.isInterface(mapperType.getKind())) {
            typeBuilder.addSuperinterface(ClassName.get(mapperType));
        } else {
            typeBuilder.superclass(TypeName.get(mapperType.asType()));
        }

        methodBodies.getBodies().forEach(body -> typeBuilder.addMethod(overrideMethod(body)));

        JavaFile.builder(packageName, typeBuilder.build()).build().writeTo(filer);
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
                .addModifiers(decisions.publicModifiers(options.isMethodsFinal()))
                .addAnnotation(Override.class)
                .returns(returnTypeName(method))
                .addCode(impl.getBody())
                // percolate: when docTags is on, bracket the WHOLE generated method (signature through
                // closing brace) so a documentation include renders the complete method. Null off-path ⇒
                // consumer output byte-for-byte unchanged (change doc-tag-whole-methods).
                .docTag(options.isDocTags() ? method.getSimpleName().toString() : null);
        method.getThrownTypes().forEach(t -> builder.addException(TypeName.get(t)));
        method.getParameters().forEach(p -> builder.addParameter(parameterSpec(p)));
        return builder.build();
    }

    ParameterSpec parameterSpec(final VariableElement parameter) {
        return ParameterSpec.builder(
                        TypeName.get(parameter.asType()),
                        parameter.getSimpleName().toString())
                .addModifiers(decisions.parameterModifiers(options.isParametersFinal()))
                .build();
    }

    static TypeName returnTypeName(final ExecutableElement method) {
        final var returnType = method.getReturnType();
        return returnType.getKind() == TypeKind.VOID ? TypeName.VOID : TypeName.get(returnType);
    }
}
