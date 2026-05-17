package io.github.joke.percolate.spi.builtins;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Bridge;
import io.github.joke.percolate.spi.BridgeStep;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.MethodCandidate;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

@AutoService(Bridge.class)
@NoArgsConstructor
public final class MethodCallBridge implements Bridge {

    private static final int SINGLE_PARAM_COUNT = 1;

    @Override
    public Stream<BridgeStep> bridge(final TypeMirror sourceType, final TypeMirror targetType, final ResolveCtx ctx) {
        final var callableMethods = ctx.callableMethods();
        if (callableMethods == null) {
            return Stream.empty();
        }

        return callableMethods.producing(targetType).collect(toUnmodifiableList()).stream()
                .filter(candidate -> {
                    final var method = candidate.getMethod();
                    final var params = method.getParameters();
                    return params.size() == SINGLE_PARAM_COUNT
                            && ctx.types()
                                    .isAssignable(sourceType, params.get(0).asType())
                            && ctx.types().isAssignable(method.getReturnType(), targetType);
                })
                .map(candidate -> {
                    final var method = candidate.getMethod();
                    final var paramType = method.getParameters().get(0).asType();
                    final var returnType = method.getReturnType();
                    final var paramDistance = subtypeDistance(sourceType, paramType, ctx);
                    final var returnDistance = subtypeDistance(returnType, targetType, ctx);
                    final var weight = Weights.METHOD + paramDistance + returnDistance;
                    return new BridgeStep(paramType, returnType, weight, renderCodegen(candidate), List.of());
                });
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
            final var currentType = current.type;
            final var depth = current.depth;
            final var elem = ctx.types().asElement(currentType);
            if (!(elem instanceof TypeElement)) {
                continue;
            }
            final var typeElement = (TypeElement) elem;
            final var directSupertypes = typeElement.getSuperclass();
            if (directSupertypes == null) {
                continue;
            }
            final var supKey = directSupertypes.toString();
            if (visited.contains(supKey)) {
                continue;
            }
            visited.add(supKey);
            if (ctx.types().isSameType(directSupertypes, target)) {
                return depth + 1;
            }
            queue.add(new Pair(directSupertypes, depth + 1));
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
