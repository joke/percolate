package io.github.joke.percolate.processor.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.spi.EdgeCodegen;
import io.github.joke.percolate.processor.spi.ResolveCtx;
import io.github.joke.percolate.processor.spi.SourceStep;
import io.github.joke.percolate.processor.spi.Step;
import io.github.joke.percolate.processor.spi.Weights;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.stream.Stream;

@AutoService(SourceStep.class)
@NoArgsConstructor
public final class GetterRead implements SourceStep {

    @Override
    public Stream<Step> stepsFrom(final TypeMirror produces, final String pathTail, final ResolveCtx ctx) {
        if (pathTail == null || pathTail.isEmpty()) {
            return Stream.empty();
        }
        if (produces.getKind() != TypeKind.DECLARED) {
            return Stream.empty();
        }
        final var element = ctx.types().asElement(produces);
        if (!(element instanceof TypeElement)) {
            return Stream.empty();
        }
        final var typeElement = (TypeElement) element;
        final var beanMethodName = "get" + Character.toUpperCase(pathTail.charAt(0)) + pathTail.substring(1);
        final var accessor = findAccessor(typeElement, beanMethodName, ctx);
        if (accessor == null) {
            final var fieldGetter = findMethod(typeElement, pathTail, ctx);
            if (fieldGetter == null) {
                return Stream.empty();
            }
            final var returnType = fieldGetter.getReturnType();
            final EdgeCodegen codegen =
                    (vars, inputs) -> CodeBlock.of("$L.$N()", inputs.single(), fieldGetter.getSimpleName());
            return Stream.of(new Step(returnType, Weights.STEP, codegen));
        }
        final var returnType = accessor.getReturnType();
        final EdgeCodegen codegen =
                (vars, inputs) -> CodeBlock.of("$L.$N()", inputs.single(), accessor.getSimpleName());
        return Stream.of(new Step(returnType, Weights.STEP, codegen));
    }

    @Nullable
    private ExecutableElement findAccessor(
            final TypeElement typeElement, final String getMethodName, final ResolveCtx ctx) {
        final var getterMethod = findMethod(typeElement, getMethodName, ctx);
        if (getterMethod != null) {
            return getterMethod;
        }
        final var isMethodName = "is" + getMethodName.substring(3);
        final var isMethod = findMethod(typeElement, isMethodName, ctx);
        if (isMethod != null && isBooleanReturn(isMethod.getReturnType(), ctx)) {
            return isMethod;
        }
        return null;
    }

    private boolean isBooleanReturn(final TypeMirror returnType, final ResolveCtx ctx) {
        if (returnType.getKind() == TypeKind.BOOLEAN) {
            return true;
        }
        if (returnType.getKind() != TypeKind.DECLARED) {
            return false;
        }
        final var elem = ctx.types().asElement(returnType);
        if (!(elem instanceof TypeElement)) {
            return false;
        }
        return "java.lang.Boolean"
                .equals(((TypeElement) elem).getQualifiedName().toString());
    }

    @Nullable
    private ExecutableElement findMethod(final TypeElement typeElement, final String name, final ResolveCtx ctx) {
        ExecutableElement result = null;
        boolean found = false;
        for (final var enclosed : ctx.elements().getAllMembers(typeElement)) {
            if (found) {
                break;
            }
            if (enclosed.getKind() != ElementKind.METHOD) {
                continue;
            }
            final var method = (ExecutableElement) enclosed;
            if (!method.getSimpleName().contentEquals(name)) {
                continue;
            }
            if (!method.getParameters().isEmpty()) {
                continue;
            }
            if (isInObjectClass(method)) {
                continue;
            }
            result = method;
            found = true;
        }
        return result;
    }

    private boolean isInObjectClass(final ExecutableElement method) {
        final var enclosing = method.getEnclosingElement();
        if (!(enclosing instanceof TypeElement)) {
            return false;
        }
        return "java.lang.Object"
                .equals(((TypeElement) enclosing).getQualifiedName().toString());
    }
}
