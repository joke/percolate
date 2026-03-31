package io.github.joke.percolate.processor.stage;

import static java.util.Comparator.comparingInt;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.graph.TransformEdge;
import io.github.joke.percolate.processor.model.ConstructorParamAccessor;
import io.github.joke.percolate.processor.model.FieldReadAccessor;
import io.github.joke.percolate.processor.model.FieldWriteAccessor;
import io.github.joke.percolate.processor.model.GetterAccessor;
import io.github.joke.percolate.processor.model.MappingMethodModel;
import io.github.joke.percolate.processor.model.ReadAccessor;
import io.github.joke.percolate.processor.transform.ResolvedMapping;
import io.github.joke.percolate.processor.transform.ResolvedModel;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.processing.Filer;
import javax.lang.model.element.PackageElement;

public final class GenerateStage {

    private final Filer filer;

    @Inject
    GenerateStage(final Filer filer) {
        this.filer = filer;
    }

    @SuppressWarnings("NullAway")
    public StageResult<JavaFile> execute(final ResolvedModel resolvedModel) {
        final var mapperType = resolvedModel.getMapperType();
        final PackageElement packageElement = (PackageElement) mapperType.getEnclosingElement();
        final var packageName = packageElement.getQualifiedName().toString();
        final var simpleName = mapperType.getSimpleName().toString();
        final var mapperName = ClassName.get(packageName, simpleName);
        final var implName = ClassName.get(packageName, simpleName + "Impl");

        final TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(implName).addModifiers(PUBLIC, FINAL).addSuperinterface(mapperName);

        for (final MappingMethodModel method : resolvedModel.getMethods()) {
            final var mappings =
                    Objects.requireNonNull(resolvedModel.getMethodMappings().get(method));
            classBuilder.addMethod(generateMethod(method, mappings));
        }

        final JavaFile javaFile =
                JavaFile.builder(mapperName.packageName(), classBuilder.build()).build();

        try {
            javaFile.writeTo(filer);
        } catch (final IOException e) {
            return StageResult.failure(
                    List.of(new Diagnostic(mapperType, "Failed to write generated file: " + e.getMessage(), ERROR)));
        }

        return StageResult.success(javaFile);
    }

    private MethodSpec generateMethod(final MappingMethodModel method, final List<ResolvedMapping> mappings) {
        final var executableElement = method.getMethod();
        final var sourceParam = executableElement.getParameters().get(0);
        final var sourceParamName = sourceParam.getSimpleName().toString();
        final var returnType = TypeName.get(method.getTargetType());

        final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(
                        executableElement.getSimpleName().toString())
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(returnType)
                .addParameter(TypeName.get(method.getSourceType()), sourceParamName);

        final boolean allConstructor =
                mappings.stream().allMatch(m -> m.getTargetAccessor() instanceof ConstructorParamAccessor);

        if (allConstructor) {
            generateConstructorBody(methodBuilder, mappings, sourceParamName, returnType);
        } else {
            generateFieldBody(methodBuilder, mappings, sourceParamName, returnType);
        }

        return methodBuilder.build();
    }

    private void generateConstructorBody(
            final MethodSpec.Builder methodBuilder,
            final List<ResolvedMapping> mappings,
            final String sourceParamName,
            final TypeName returnType) {

        final List<ResolvedMapping> sorted = new ArrayList<>(mappings);
        @SuppressWarnings("NullAway") // targetAccessor is ConstructorParamAccessor in allConstructor branch
        final var comparator =
                comparingInt((ResolvedMapping m) -> ((ConstructorParamAccessor) m.getTargetAccessor()).getParamIndex());
        sorted.sort(comparator);

        final List<CodeBlock> args = new ArrayList<>();
        for (final ResolvedMapping mapping : sorted) {
            args.add(generateValueExpression(mapping, sourceParamName));
        }

        methodBuilder.addStatement("return new $T($L)", returnType, CodeBlock.join(args, ", "));
    }

    private void generateFieldBody(
            final MethodSpec.Builder methodBuilder,
            final List<ResolvedMapping> mappings,
            final String sourceParamName,
            final TypeName returnType) {

        methodBuilder.addStatement("$T target = new $T()", returnType, returnType);

        for (final ResolvedMapping mapping : mappings) {
            if (mapping.getTargetAccessor() instanceof FieldWriteAccessor) {
                final var valueExpr = generateValueExpression(mapping, sourceParamName);
                methodBuilder.addStatement("target.$L = $L", mapping.getTargetName(), valueExpr);
            }
        }

        methodBuilder.addStatement("return target");
    }

    private CodeBlock generateValueExpression(final ResolvedMapping mapping, final String sourceParamName) {
        var result = generateReadChainExpression(mapping.getSourceChain(), sourceParamName);
        for (final TransformEdge edge : mapping.getEdges()) {
            result = edge.getCodeTemplate().apply(result);
        }
        return result;
    }

    private static CodeBlock generateReadChainExpression(final List<ReadAccessor> chain, final String sourceParamName) {
        var expr = CodeBlock.of("$L", sourceParamName);
        for (final ReadAccessor accessor : chain) {
            expr = appendAccessor(expr, accessor);
        }
        return expr;
    }

    private static CodeBlock appendAccessor(final CodeBlock base, final ReadAccessor accessor) {
        if (accessor instanceof GetterAccessor) {
            final GetterAccessor getter = (GetterAccessor) accessor;
            return CodeBlock.of("$L.$L()", base, getter.getMethod().getSimpleName());
        }
        if (accessor instanceof FieldReadAccessor) {
            return CodeBlock.of("$L.$L", base, accessor.getName());
        }
        return base;
    }
}
