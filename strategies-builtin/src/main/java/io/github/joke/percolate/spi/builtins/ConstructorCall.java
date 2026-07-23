package io.github.joke.percolate.spi.builtins;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.ClassName;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * Assembles the demanded type by calling one of its constructors: a multi-port {@link OperationSpec} whose ports
 * are the constructor parameters, named after them. It is gated by the demand's declared-children goal spec — a
 * constructor is a candidate only when its parameter-name set equals {@link ProduceDemand#declaredChildren()} — so a
 * zero-parameter constructor is never chosen over the user's declared mapping, and assembly never recurses
 * unboundedly. Each port's nullness is resolved through the demand's nullness oracle. It is a plain
 * {@link ExpansionStrategy} in the one unified loader list; "assembly" is an emission-time gating concern, not a
 * separate result type or a driver routing branch.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class ConstructorCall implements ExpansionStrategy {

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var targetType = demand.targetType();
        final var typeElement = ctx.asTypeElement(targetType).orElse(null);
        if (typeElement == null) {
            return Stream.empty();
        }
        final var declared = demand.declaredChildren();
        if (declared.isEmpty()) {
            // A leaf demand (no declared children) is never assembled: a zero-arg constructor must not vacuously
            // satisfy it (no silent sourcing). Assembly fires only for a target level with declared children.
            return Stream.empty();
        }
        return ctx.membersOf(typeElement)
                .filter(ctx::isConstructor)
                .map(ExecutableElement.class::cast)
                .filter(ctor -> !ctx.isPrivate(ctor))
                .filter(ctor -> parameterNames(ctor).equals(declared))
                .map(ctor -> buildSpec(ctor, typeElement, targetType, demand));
    }

    static Set<String> parameterNames(final ExecutableElement ctor) {
        return ctor.getParameters().stream()
                .map(param -> param.getSimpleName().toString())
                .collect(toUnmodifiableSet());
    }

    OperationSpec buildSpec(
            final ExecutableElement ctor,
            final TypeElement typeElement,
            final TypeMirror targetType,
            final ProduceDemand demand) {
        final List<Port> ports = ctor.getParameters().stream()
                .map(param -> Port.subTarget(
                        param.getSimpleName().toString(), param.asType(), demand.nullnessOf(param.asType(), param)))
                .collect(toUnmodifiableList());
        final List<String> portNames = ports.stream().map(Port::getName).collect(toUnmodifiableList());
        return OperationSpec.of(
                constructorLabel(typeElement, ports),
                buildCodegen(typeElement, portNames),
                Weights.STEP,
                ports,
                targetType,
                Nullability.NON_NULL);
    }

    static String constructorLabel(final TypeElement typeElement, final List<Port> ports) {
        final var params =
                ports.stream().map(port -> Labels.simple(port.getType())).collect(joining(", "));
        return "new " + typeElement.getSimpleName() + "(" + params + ")";
    }

    OperationCodegen buildCodegen(final TypeElement typeElement, final List<String> portNames) {
        return inputs -> {
            final var args = portNames.stream().map(inputs::byName).collect(CodeBlock.joining(", "));
            return CodeBlock.builder()
                    .add("new $T($L)", ClassName.get(typeElement), args)
                    .build();
        };
    }
}
