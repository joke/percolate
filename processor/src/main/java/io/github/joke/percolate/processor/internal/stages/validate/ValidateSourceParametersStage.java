package io.github.joke.percolate.processor.internal.stages.validate;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.stages.Stage;
import io.github.joke.percolate.processor.model.Bind;
import io.github.joke.percolate.processor.model.MethodDirectives;
import io.github.joke.percolate.processor.model.ScopeInputOverride;
import io.github.joke.percolate.spi.Subjects;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

import static io.github.joke.percolate.processor.Diagnostic.error;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static javax.lang.model.type.TypeKind.TYPEVAR;

// The engine's own rules about its own scope inputs (design D7 of change decouple-engine-from-strategy-
// semantics): a bound source path must root at a scope input of the method — a path it cannot begin, not a
// property of @Map's shape — and two scope inputs of one method may not share a name, because a name is how a
// BY_NAME port selects and a shared one makes that selection ambiguous. A scope input's name is the parameter's
// own simple name unless a reader published an override via scopeInput (e.g. @Ambient's rename), so both rules
// hold for any reader and name no annotation.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidateSourceParametersStage implements Stage {

    @Override
    public void run(final MapperContext ctx) {
        final var methodDirectives = ctx.getMethodDirectives();
        if (methodDirectives == null) {
            return;
        }
        methodDirectives.forEach(directives -> validate(directives, ctx));
    }

    void validate(final MethodDirectives directives, final MapperContext ctx) {
        final var overrideByParam = overridesByParameter(directives);
        final var methodSig = formatMethodSig(directives.getMethod());
        checkDistinctScopeInputs(directives, overrideByParam, methodSig, ctx);
        final var names = scopeInputNames(directives, overrideByParam);
        directives.getBinds().forEach(bind -> checkBind(bind, names, methodSig, ctx));
    }

    // The reader-published override per parameter, if any; a parameter named twice keeps the first override.
    Map<VariableElement, ScopeInputOverride> overridesByParameter(final MethodDirectives directives) {
        return directives.getScopeInputOverrides().stream()
                .collect(toMap(ScopeInputOverride::getParameter, override -> override, (first, second) -> first));
    }

    // The method's scope-input names: a parameter's own simple name, or a reader's published override.
    Set<String> scopeInputNames(
            final MethodDirectives directives, final Map<VariableElement, ScopeInputOverride> overrideByParam) {
        return directives.getMethod().getParameters().stream()
                .map(param -> nameOf(param, overrideByParam))
                .collect(toUnmodifiableSet());
    }

    // Two scope inputs of one method published under one name: every occurrence after the first is an error,
    // positioned at its own parameter. Ordinary parameters cannot collide in Java, so a collision always involves
    // at least one reader-published override.
    void checkDistinctScopeInputs(
            final MethodDirectives directives,
            final Map<VariableElement, ScopeInputOverride> overrideByParam,
            final String methodSig,
            final MapperContext ctx) {
        directives.getMethod().getParameters().stream()
                .collect(groupingBy(param -> nameOf(param, overrideByParam), LinkedHashMap::new, toUnmodifiableList()))
                .forEach((name, sharing) -> reportDuplicateScopeInputs(name, sharing, methodSig, ctx));
    }

    void reportDuplicateScopeInputs(
            final String name,
            final List<? extends VariableElement> sharing,
            final String methodSig,
            final MapperContext ctx) {
        sharing.stream()
                .skip(1)
                .forEach(param -> ctx.report(
                        error(Subjects.of(param, null, null), "duplicate scope input '" + name + "' on " + methodSig)
                                .asPermanent()));
    }

    String nameOf(final VariableElement param, final Map<VariableElement, ScopeInputOverride> overrideByParam) {
        final var override = overrideByParam.get(param);
        return override == null ? param.getSimpleName().toString() : override.getName();
    }

    void checkBind(final Bind bind, final Set<String> names, final String methodSig, final MapperContext ctx) {
        final var source = bind.getSourcePath();
        if (source.isEmpty()) {
            return;
        }
        final var root = source.get(0);
        if (names.contains(root)) {
            return;
        }
        ctx.report(
                error(bind.getSubject(), "source path is rooted at unknown scope input '" + root + "' on " + methodSig)
                        .asPermanent());
    }

    String formatMethodSig(final ExecutableElement method) {
        final var name = method.getSimpleName().toString();
        final var paramTypes = method.getParameters().stream()
                .map(p -> simpleTypeName(p.asType()))
                .collect(joining(","));
        return name + "(" + paramTypes + ")";
    }

    String simpleTypeName(final TypeMirror mirror) {
        if (mirror == null) {
            return "?";
        }
        if (mirror.getKind() == TYPEVAR) {
            return mirror.toString();
        }
        return declaredSimpleName(mirror).orElseGet(mirror::toString);
    }

    Optional<String> declaredSimpleName(final TypeMirror mirror) {
        if (!(mirror instanceof DeclaredType)) {
            return Optional.empty();
        }
        final var elem = ((DeclaredType) mirror).asElement();
        return elem instanceof TypeElement ? Optional.of(elem.getSimpleName().toString()) : Optional.empty();
    }
}
