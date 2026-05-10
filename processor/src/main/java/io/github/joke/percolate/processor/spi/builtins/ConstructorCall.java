package io.github.joke.percolate.processor.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.spi.GroupBuild;
import io.github.joke.percolate.processor.spi.GroupCodegen;
import io.github.joke.percolate.processor.spi.GroupTarget;
import io.github.joke.percolate.processor.spi.ResolveCtx;
import io.github.joke.percolate.processor.spi.Slot;
import io.github.joke.percolate.processor.spi.Weights;
import lombok.NoArgsConstructor;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@AutoService(GroupTarget.class)
@NoArgsConstructor
public final class ConstructorCall implements GroupTarget {

    @Override
    public Optional<GroupBuild> buildFor(
            final TypeMirror returnType, final List<String> targetTails, final ResolveCtx ctx) {
        if (targetTails == null || targetTails.isEmpty()) {
            return Optional.empty();
        }

        final var actualType = returnType;
        if (actualType.getKind() != TypeKind.DECLARED) {
            return Optional.empty();
        }

        final var element = ctx.types().asElement(actualType);
        if (!(element instanceof TypeElement)) {
            return Optional.empty();
        }
        @SuppressWarnings("cast")
        final var typeElement = (TypeElement) element;

        final var requiredNames = Set.copyOf(targetTails);

        // Find constructors whose parameter name set exactly matches targetTails
        ExecutableElement bestCtor = null;
        var nameMatch = false;

        for (final var enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            final var ctor = (ExecutableElement) enclosed;
            final var params = ctor.getParameters();

            if (params.size() != requiredNames.size()) {
                continue;
            }

            final Set<String> paramNames = new HashSet<>();
            for (final var param : params) {
                paramNames.add(param.getSimpleName().toString());
            }

            if (paramNames.equals(requiredNames)) {
                bestCtor = ctor;
                nameMatch = true;
                break;
            }
        }

        // Fall back to field-name matching when parameter names are not preserved
        if (bestCtor == null) {
            final List<String> fieldNames = new ArrayList<>();
            for (final var enclosed : typeElement.getEnclosedElements()) {
                if (enclosed.getKind() != ElementKind.FIELD) {
                    continue;
                }
                fieldNames.add(enclosed.getSimpleName().toString());
            }

            if (fieldNames.size() == targetTails.size() && new HashSet<>(fieldNames).equals(requiredNames)) {
                for (final var enclosed : typeElement.getEnclosedElements()) {
                    if (enclosed.getKind() != ElementKind.CONSTRUCTOR) {
                        continue;
                    }
                    final var ctor = (ExecutableElement) enclosed;
                    final var params = ctor.getParameters();

                    if (params.size() == targetTails.size()) {
                        bestCtor = ctor;
                        break;
                    }
                }
            }
        }

        if (bestCtor == null) {
            return Optional.empty();
        }

        final var ctor = bestCtor;
        final var targetElement = typeElement;

        // Build slots in constructor declaration order
        final List<Slot> slots = new ArrayList<>();
        final List<String> slotNames = new ArrayList<>();
        final List<String> fieldNames = new ArrayList<>();
        for (final var enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                fieldNames.add(enclosed.getSimpleName().toString());
            }
        }
        final var fieldMatch = !nameMatch
                && fieldNames.size() == targetTails.size()
                && new HashSet<>(fieldNames).equals(requiredNames);

        for (var i = 0; i < ctor.getParameters().size(); i++) {
            final var param = ctor.getParameters().get(i);
            final String paramName;
            if (nameMatch) {
                paramName = param.getSimpleName().toString();
            } else if (fieldMatch && i < fieldNames.size()) {
                paramName = fieldNames.get(i);
            } else {
                paramName = i < targetTails.size() ? targetTails.get(i) : "arg" + i;
            }
            slotNames.add(paramName);
            slots.add(new Slot(paramName, param.asType(), Weights.STEP));
        }

        // GroupCodegen for group coordination
        final GroupCodegen groupCodegen = (vars, inputs) -> {
            final var builder = CodeBlock.builder().add("new $T(", targetElement.getQualifiedName());
            for (var i = 0; i < slotNames.size(); i++) {
                if (i > 0) {
                    builder.add(", ");
                }
                builder.add("$L", slotNames.get(i));
            }
            builder.add(")");
            return builder.build();
        };

        return Optional.of(new GroupBuild(slots, groupCodegen));
    }
}
