package io.github.joke.percolate.spi.builtins;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.ExpansionStep;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Frontier;
import io.github.joke.percolate.spi.MethodCandidate;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Slot;
import io.github.joke.percolate.spi.Weights;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * Produces the frontier's target type by calling a single-argument callable method that returns it: a
 * {@link io.github.joke.percolate.spi.Intent#BOUNDARY} step with one slot per argument (here always one). The slot
 * is the method argument, produced in turn from the in-scope source; the realised edge renders
 * {@code receiver.method(arg)}.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class MethodCallBridge implements ExpansionStrategy {

    private static final int SINGLE_PARAM_COUNT = 1;

    @Override
    public Stream<ExpansionStep> expand(final Frontier frontier, final ResolveCtx ctx) {
        final var callableMethods = ctx.callableMethods();
        if (callableMethods == null) {
            return Stream.empty();
        }
        final var targetType = frontier.targetType();
        return callableMethods.producing(targetType).collect(toUnmodifiableList()).stream()
                .filter(candidate -> {
                    final var method = candidate.getMethod();
                    return method.getParameters().size() == SINGLE_PARAM_COUNT
                            && ctx.types().isAssignable(method.getReturnType(), targetType);
                })
                .map(candidate -> buildStep(candidate, targetType, ctx));
    }

    private ExpansionStep buildStep(
            final MethodCandidate candidate, final TypeMirror targetType, final ResolveCtx ctx) {
        final var method = candidate.getMethod();
        final var param = method.getParameters().get(0);
        final var returnType = method.getReturnType();
        final var returnDistance = subtypeDistance(returnType, targetType, ctx);
        final var weight = Weights.METHOD + returnDistance;
        final var slot = new Slot(param.getSimpleName().toString(), param.asType(), Weights.STEP, param);
        return ExpansionStep.boundary(List.of(slot), returnType, renderCodegen(candidate), weight);
    }

    private EdgeCodegen renderCodegen(final MethodCandidate candidate) {
        final var receiver = candidate.getReceiver().asExpression();
        final var method = candidate.getMethod();
        final var methodName = method.getSimpleName().toString();
        return (vars, inputs) -> CodeBlock.of("$L.$N($L)", receiver, methodName, inputs.single());
    }

    private int subtypeDistance(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        if (ctx.types().isSameType(from, to)) {
            return 0;
        }
        if (!ctx.types().isAssignable(from, to)) {
            return 0;
        }
        return bfsDistance(from, to, ctx);
    }

    private int bfsDistance(final TypeMirror start, final TypeMirror target, final ResolveCtx ctx) {
        if (ctx.types().isSameType(start, target)) {
            return 0;
        }
        final Set<String> visited = new HashSet<>();
        final List<Pair> queue = new ArrayList<>();
        queue.add(new Pair(start, 0));
        visited.add(start.toString());
        while (!queue.isEmpty()) {
            final var current = queue.remove(0);
            final var elem = ctx.types().asElement(current.type);
            if (!(elem instanceof TypeElement)) {
                continue;
            }
            final var directSupertype = ((TypeElement) elem).getSuperclass();
            if (directSupertype == null) {
                continue;
            }
            final var supKey = directSupertype.toString();
            if (visited.contains(supKey)) {
                continue;
            }
            visited.add(supKey);
            if (ctx.types().isSameType(directSupertype, target)) {
                return current.depth + 1;
            }
            queue.add(new Pair(directSupertype, current.depth + 1));
        }
        return 0;
    }

    private static final class Pair {
        final TypeMirror type;
        final int depth;

        private Pair(final TypeMirror type, final int depth) {
            this.type = type;
            this.depth = depth;
        }
    }
}
