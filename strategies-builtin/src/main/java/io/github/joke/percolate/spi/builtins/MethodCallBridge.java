package io.github.joke.percolate.spi.builtins;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Demand;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.MethodCandidate;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
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

/**
 * Produces the demanded type by calling a single-argument callable method that returns it: a one-port
 * {@link OperationSpec} whose port is the method argument, produced in turn from the in-scope source. The operation
 * renders {@code receiver.method(arg)}. The argument port's and produced value's nullness are resolved through the
 * demand oracle.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class MethodCallBridge implements ExpansionStrategy {

    private static final int SINGLE_PARAM_COUNT = 1;

    @Override
    public Stream<OperationSpec> expand(final Demand demand, final ResolveCtx ctx) {
        if (!demand.declaredChildren().isEmpty()) {
            // A target the user declared field-by-field is assembled, not produced by a method call: a method-call
            // bridge applies only to leaf demands. This also keeps a mapper's own method from satisfying its own
            // return root (a degenerate self-call) at an assembly demand.
            return Stream.empty();
        }
        final var callableMethods = ctx.callableMethods();
        if (callableMethods == null) {
            return Stream.empty();
        }
        final var targetType = demand.targetType();
        return callableMethods.producing(targetType).collect(toUnmodifiableList()).stream()
                .filter(candidate -> {
                    final var method = candidate.getMethod();
                    return method.getParameters().size() == SINGLE_PARAM_COUNT
                            && ctx.types().isAssignable(method.getReturnType(), targetType);
                })
                .map(candidate -> buildSpec(candidate, targetType, demand, ctx));
    }

    private OperationSpec buildSpec(
            final MethodCandidate candidate, final TypeMirror targetType, final Demand demand, final ResolveCtx ctx) {
        final var method = candidate.getMethod();
        final var param = method.getParameters().get(0);
        final var returnType = method.getReturnType();
        final var returnDistance = subtypeDistance(returnType, targetType, ctx);
        final var weight = Weights.METHOD + returnDistance;
        final var port =
                new Port(param.getSimpleName().toString(), param.asType(), demand.nullnessOf(param.asType(), param));
        return OperationSpec.of(
                method.getSimpleName() + "(…)",
                renderCodegen(candidate),
                weight,
                List.of(port),
                returnType,
                demand.nullnessOf(returnType, method));
    }

    private OperationCodegen renderCodegen(final MethodCandidate candidate) {
        final var receiver = candidate.getReceiver().asExpression();
        final var method = candidate.getMethod();
        final var methodName = method.getSimpleName().toString();
        return inputs -> CodeBlock.of("$L.$N($L)", receiver, methodName, inputs.single());
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
