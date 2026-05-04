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
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

@AutoService(GroupTarget.class)
public final class ConstructorCall implements GroupTarget {

    @Override
    public Optional<GroupBuild> buildFor(TypeMirror returnType, List<String> targetTails, ResolveCtx ctx) {
        if (targetTails == null || targetTails.isEmpty()) {
            return Optional.empty();
        }

        TypeMirror actualType = returnType;
        if (actualType.getKind() != TypeKind.DECLARED) {
            return Optional.empty();
        }

        Element element = ctx.types().asElement(actualType);
        if (!(element instanceof TypeElement)) {
            return Optional.empty();
        }
        @SuppressWarnings("cast")
        TypeElement typeElement = (TypeElement) element;

        Set<String> requiredNames = Set.copyOf(targetTails);

        // Find constructors whose parameter name set exactly matches targetTails
        ExecutableElement bestCtor = null;
        boolean nameMatch = false;

        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            ExecutableElement ctor = (ExecutableElement) enclosed;
            List<? extends VariableElement> params = ctor.getParameters();

            if (params.size() != requiredNames.size()) {
                continue;
            }

            Set<String> paramNames = new HashSet<>();
            for (VariableElement param : params) {
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
            List<String> fieldNames = new ArrayList<>();
            for (Element enclosed : typeElement.getEnclosedElements()) {
                if (enclosed.getKind() != ElementKind.FIELD) {
                    continue;
                }
                fieldNames.add(enclosed.getSimpleName().toString());
            }

            if (fieldNames.size() == targetTails.size() && new HashSet<>(fieldNames).equals(requiredNames)) {
                for (Element enclosed : typeElement.getEnclosedElements()) {
                    if (enclosed.getKind() != ElementKind.CONSTRUCTOR) {
                        continue;
                    }
                    ExecutableElement ctor = (ExecutableElement) enclosed;
                    List<? extends VariableElement> params = ctor.getParameters();

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

        final ExecutableElement ctor = bestCtor;
        final TypeElement targetElement = typeElement;

        // Build slots in constructor declaration order
        List<Slot> slots = new ArrayList<>();
        final List<String> slotNames = new ArrayList<>();
        final List<String> fieldNames = new ArrayList<>();
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                fieldNames.add(enclosed.getSimpleName().toString());
            }
        }
        final boolean fieldMatch = !nameMatch
                && fieldNames.size() == targetTails.size()
                && new HashSet<>(fieldNames).equals(requiredNames);

        for (int i = 0; i < ctor.getParameters().size(); i++) {
            VariableElement param = ctor.getParameters().get(i);
            String paramName;
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
        GroupCodegen groupCodegen = (vars, inputs) -> {
            CodeBlock.Builder builder = CodeBlock.builder().add("new $T(", targetElement.getQualifiedName());
            for (int i = 0; i < slotNames.size(); i++) {
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
