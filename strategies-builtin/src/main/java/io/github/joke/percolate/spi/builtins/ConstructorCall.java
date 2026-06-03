package io.github.joke.percolate.spi.builtins;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.AssemblyStrategy;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.ExpansionStep;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Frontier;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Slot;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Assembles the frontier's target type by calling one of its constructors: a multi-slot
 * {@link io.github.joke.percolate.spi.Intent#BOUNDARY} step whose slots are the constructor parameters, named after
 * them. It is myopic and over-emits — a step for every accessible constructor of any constructable target — and the
 * driver binds each slot, by name, to the pre-seeded target leaf of the same name. Constructions whose slots cannot
 * all resolve go UNSAT and are pruned by the fixed-point loop and the cost oracle.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class ConstructorCall implements AssemblyStrategy {

    @Override
    public Stream<ExpansionStep> expand(final Frontier frontier, final ResolveCtx ctx) {
        final var targetType = frontier.targetType();
        final var typeElement = resolveTypeElement(targetType, ctx);
        if (typeElement == null) {
            return Stream.empty();
        }
        return typeElement.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
                .map(ExecutableElement.class::cast)
                .filter(ctor -> !ctor.getModifiers().contains(Modifier.PRIVATE))
                .map(ctor -> buildStep(ctor, typeElement, targetType));
    }

    @Nullable
    private TypeElement resolveTypeElement(final TypeMirror targetType, final ResolveCtx ctx) {
        if (targetType.getKind() != TypeKind.DECLARED) {
            return null;
        }
        final var element = ctx.types().asElement(targetType);
        return element instanceof TypeElement ? (TypeElement) element : null;
    }

    private ExpansionStep buildStep(
            final ExecutableElement ctor, final TypeElement typeElement, final TypeMirror targetType) {
        final List<Slot> slots = ctor.getParameters().stream()
                .map(param -> new Slot(param.getSimpleName().toString(), param.asType(), Weights.STEP, param))
                .collect(toUnmodifiableList());
        final List<String> slotNames = slots.stream().map(Slot::getName).collect(toUnmodifiableList());
        return ExpansionStep.boundary(slots, targetType, buildCodegen(typeElement, slotNames), Weights.STEP);
    }

    private EdgeCodegen buildCodegen(final TypeElement typeElement, final List<String> slotNames) {
        return (vars, inputs) -> {
            final var builder = CodeBlock.builder().add("new $T(", ClassName.get(typeElement));
            for (var i = 0; i < slotNames.size(); i++) {
                if (i > 0) {
                    builder.add(", ");
                }
                builder.add("$L", inputs.byName(slotNames.get(i)));
            }
            builder.add(")");
            return builder.build();
        };
    }
}
