package io.github.joke.percolate.processor.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.spi.GroupBuild;
import io.github.joke.percolate.processor.spi.GroupCodegen;
import io.github.joke.percolate.processor.spi.GroupTarget;
import io.github.joke.percolate.processor.spi.ResolveCtx;
import io.github.joke.percolate.processor.spi.Slot;
import io.github.joke.percolate.processor.spi.Weights;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@AutoService(GroupTarget.class)
@NoArgsConstructor
public final class ConstructorCall implements GroupTarget {

    @Override
    public Optional<GroupBuild> buildFor(
            final TypeMirror returnType, final List<String> targetTails, final ResolveCtx ctx) {
        if (targetTails == null || targetTails.isEmpty()) {
            return Optional.empty();
        }
        final var typeElement = resolveTypeElement(returnType, ctx);
        if (typeElement == null) {
            return Optional.empty();
        }
        final var requiredNames = Set.copyOf(targetTails);

        final var ctorByParams = findConstructorByParamNames(typeElement, requiredNames);
        if (ctorByParams.isPresent()) {
            return Optional.of(buildGroup(ctorByParams.get(), typeElement, true, targetTails, requiredNames));
        }

        return buildGroupByFieldNames(typeElement, targetTails, requiredNames);
    }

    @Nullable
    private TypeElement resolveTypeElement(final TypeMirror returnType, final ResolveCtx ctx) {
        if (returnType.getKind() != TypeKind.DECLARED) {
            return null;
        }
        final var element = ctx.types().asElement(returnType);
        return element instanceof TypeElement ? (TypeElement) element : null;
    }

    private Optional<ExecutableElement> findConstructorByParamNames(
            final TypeElement typeElement, final Set<String> requiredNames) {
        for (final var enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            final var ctor = (ExecutableElement) enclosed;
            if (ctor.getParameters().size() != requiredNames.size()) {
                continue;
            }
            final Set<String> paramNames = new HashSet<>();
            for (final var param : ctor.getParameters()) {
                paramNames.add(param.getSimpleName().toString());
            }
            if (paramNames.equals(requiredNames)) {
                return Optional.of(ctor);
            }
        }
        return Optional.empty();
    }

    private Optional<GroupBuild> buildGroupByFieldNames(
            final TypeElement typeElement, final List<String> targetTails, final Set<String> requiredNames) {
        final var fieldNames = collectFieldNames(typeElement);
        if (fieldNames.size() != targetTails.size() || !new HashSet<>(fieldNames).equals(requiredNames)) {
            return Optional.empty();
        }
        final var ctorByArity = findConstructorByArity(typeElement, targetTails.size());
        return ctorByArity.map(ctor -> buildGroup(ctor, typeElement, false, targetTails, requiredNames));
    }

    private List<String> collectFieldNames(final TypeElement typeElement) {
        final List<String> fieldNames = new ArrayList<>();
        for (final var enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                fieldNames.add(enclosed.getSimpleName().toString());
            }
        }
        return fieldNames;
    }

    private Optional<ExecutableElement> findConstructorByArity(final TypeElement typeElement, final int arity) {
        for (final var enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            final var ctor = (ExecutableElement) enclosed;
            if (ctor.getParameters().size() == arity) {
                return Optional.of(ctor);
            }
        }
        return Optional.empty();
    }

    private GroupBuild buildGroup(
            final ExecutableElement ctor,
            final TypeElement typeElement,
            final boolean nameMatch,
            final List<String> targetTails,
            final Set<String> requiredNames) {
        final var fieldNames = nameMatch ? List.<String>of() : collectFieldNames(typeElement);
        final var fieldMatch = !nameMatch
                && fieldNames.size() == targetTails.size()
                && new HashSet<>(fieldNames).equals(requiredNames);

        final List<Slot> slots = new ArrayList<>();
        final List<String> slotNames = new ArrayList<>();
        final var params = ctor.getParameters();

        for (var i = 0; i < params.size(); i++) {
            final var param = params.get(i);
            final var paramName = resolveParamName(
                    nameMatch,
                    fieldMatch,
                    fieldNames,
                    targetTails,
                    param.getSimpleName().toString(),
                    i);
            slotNames.add(paramName);
            slots.add(new Slot(paramName, param.asType(), Weights.STEP));
        }

        return new GroupBuild(slots, buildCodegen(typeElement, slotNames));
    }

    private String resolveParamName(
            final boolean nameMatch,
            final boolean fieldMatch,
            final List<String> fieldNames,
            final List<String> targetTails,
            final String parameterName,
            final int index) {
        if (nameMatch) {
            return parameterName;
        }
        if (fieldMatch && index < fieldNames.size()) {
            return fieldNames.get(index);
        }
        return index < targetTails.size() ? targetTails.get(index) : "arg" + index;
    }

    private GroupCodegen buildCodegen(final TypeElement typeElement, final List<String> slotNames) {
        return (vars, inputs) -> {
            final var builder = CodeBlock.builder().add("new $T(", typeElement.getQualifiedName());
            for (var i = 0; i < slotNames.size(); i++) {
                if (i > 0) {
                    builder.add(", ");
                }
                builder.add("$L", slotNames.get(i));
            }
            builder.add(")");
            return builder.build();
        };
    }
}
