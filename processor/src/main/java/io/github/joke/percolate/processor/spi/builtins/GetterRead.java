package io.github.joke.percolate.processor.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.spi.EdgeCodegen;
import io.github.joke.percolate.processor.spi.ResolveCtx;
import io.github.joke.percolate.processor.spi.SourceStep;
import io.github.joke.percolate.processor.spi.Step;
import io.github.joke.percolate.processor.spi.Weights;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

@AutoService(SourceStep.class)
public final class GetterRead implements SourceStep {

    @Override
    public Stream<Step> stepsFrom(final TypeMirror produces, final String pathTail, final ResolveCtx ctx) {
        if (pathTail == null || pathTail.isEmpty()) {
            return Stream.empty();
        }
        if (produces.getKind() != TypeKind.DECLARED) {
            return Stream.empty();
        }
        final Element element = ctx.types().asElement(produces);
        if (!(element instanceof TypeElement)) {
            return Stream.empty();
        }
        final TypeElement typeElement = (TypeElement) element;
        final String beanMethodName = "get" + Character.toUpperCase(pathTail.charAt(0)) + pathTail.substring(1);
        final ExecutableElement accessor = findAccessor(typeElement, beanMethodName, ctx);
        if (accessor == null) {
            final ExecutableElement fieldGetter = findMethod(typeElement, pathTail, ctx);
            if (fieldGetter == null) {
                return Stream.empty();
            }
            final TypeMirror returnType = fieldGetter.getReturnType();
            final EdgeCodegen codegen =
                    (vars, inputs) -> CodeBlock.of("$L.$N()", inputs.single(), fieldGetter.getSimpleName());
            return Stream.of(new Step(returnType, Weights.STEP, codegen));
        }
        final TypeMirror returnType = accessor.getReturnType();
        final EdgeCodegen codegen =
                (vars, inputs) -> CodeBlock.of("$L.$N()", inputs.single(), accessor.getSimpleName());
        return Stream.of(new Step(returnType, Weights.STEP, codegen));
    }

    @Nullable
    private ExecutableElement findAccessor(
            final TypeElement typeElement, final String getMethodName, final ResolveCtx ctx) {
        final ExecutableElement getterMethod = findMethod(typeElement, getMethodName, ctx);
        if (getterMethod != null) {
            return getterMethod;
        }
        final String isMethodName = "is" + getMethodName.substring(3);
        final ExecutableElement isMethod = findMethod(typeElement, isMethodName, ctx);
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
        final Element elem = ctx.types().asElement(returnType);
        if (!(elem instanceof TypeElement)) {
            return false;
        }
        return "java.lang.Boolean"
                .equals(((TypeElement) elem).getQualifiedName().toString());
    }

    @Nullable
    private ExecutableElement findMethod(final TypeElement typeElement, final String name, final ResolveCtx ctx) {
        for (final Element enclosed : ctx.elements().getAllMembers(typeElement)) {
            if (enclosed.getKind() != ElementKind.METHOD) {
                continue;
            }
            final ExecutableElement method = (ExecutableElement) enclosed;
            if (!method.getSimpleName().contentEquals(name)) {
                continue;
            }
            if (!method.getParameters().isEmpty()) {
                continue;
            }
            if (isInObjectClass(method)) {
                continue;
            }
            return method;
        }
        return null;
    }

    private boolean isInObjectClass(final ExecutableElement method) {
        final Element enclosing = method.getEnclosingElement();
        if (!(enclosing instanceof TypeElement)) {
            return false;
        }
        return "java.lang.Object"
                .equals(((TypeElement) enclosing).getQualifiedName().toString());
    }
}
