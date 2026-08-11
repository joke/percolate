package io.github.joke.percolate.spi.builtins.assembly;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.ClassName;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Offer;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.builtins.Labels;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

import static io.github.joke.percolate.spi.Nullability.NON_NULL;
import static io.github.joke.percolate.spi.Weights.STEP;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

// Assembles the demanded type by calling one of its constructors: a multi-port OperationSpec whose ports are
// the constructor parameters, named after them. It is gated by the demand's declared-children goal spec — a
// constructor is a candidate only when its parameter-name set equals ProduceDemand.declaredChildren() — so a
// zero-parameter constructor is never chosen over the user's declared mapping, and assembly never recurses
// unboundedly. Each port's nullness is resolved through the demand's nullness oracle. It is a plain
// ExpansionStrategy in the one unified loader list; "assembly" is an emission-time gating concern, not a
// separate result type or a driver routing branch.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class ConstructorCall implements ExpansionStrategy {

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
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
                .flatMap(member -> candidateConstructor(member, declared, ctx))
                .map(ctor -> buildSpec(ctor, typeElement, targetType, demand))
                .map(Offer::of);
    }

    // member as the constructor this demand can call — non-private, its parameter names exactly the declared
    // children — or nothing. The narrowing cast is a plain cast rather than a mapping step: an element that
    // answers isConstructor is executable by construction, so a mapped cast would only be untestable ceremony.
    Stream<ExecutableElement> candidateConstructor(
            final Element member, final Set<String> declared, final ResolveCtx ctx) {
        if (!ctx.isConstructor(member)) {
            return Stream.empty();
        }
        final var ctor = (ExecutableElement) member;
        return !ctx.isPrivate(ctor) && parameterNames(ctor).equals(declared) ? Stream.of(ctor) : Stream.empty();
    }

    Set<String> parameterNames(final ExecutableElement ctor) {
        return ctor.getParameters().stream()
                .map(param -> param.getSimpleName().toString())
                .collect(toUnmodifiableSet());
    }

    OperationSpec buildSpec(
            final ExecutableElement ctor,
            final TypeElement typeElement,
            final TypeMirror targetType,
            final ProduceDemand demand) {
        final var ports = ctor.getParameters().stream()
                .map(param -> Port.subTarget(
                        param.getSimpleName().toString(), param.asType(), demand.nullnessOf(param.asType(), param)))
                .collect(toUnmodifiableList());
        final var portNames = ports.stream().map(Port::getName).collect(toUnmodifiableList());
        return OperationSpec.of(
                constructorLabel(typeElement, ports),
                buildCodegen(typeElement, portNames),
                STEP,
                ports,
                targetType,
                NON_NULL);
    }

    String constructorLabel(final TypeElement typeElement, final List<Port> ports) {
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
