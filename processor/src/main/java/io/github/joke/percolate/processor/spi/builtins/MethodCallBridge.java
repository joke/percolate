package io.github.joke.percolate.processor.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.spi.Bridge;
import io.github.joke.percolate.processor.spi.BridgeStep;
import io.github.joke.percolate.processor.spi.CallableMethods;
import io.github.joke.percolate.processor.spi.EdgeCodegen;
import io.github.joke.percolate.processor.spi.IncomingValues;
import io.github.joke.percolate.processor.spi.MethodCandidate;
import io.github.joke.percolate.processor.spi.ResolveCtx;
import io.github.joke.percolate.processor.spi.Weights;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

@AutoService(Bridge.class)
public final class MethodCallBridge implements Bridge {

    @Override
    public Stream<BridgeStep> bridge(
            final TypeMirror sourceType, final TypeMirror targetType, final ResolveCtx ctx) {
        final CallableMethods callableMethods = ctx.callableMethods();
        if (callableMethods == null) {
            return Stream.empty();
        }

        final List<BridgeStep> steps = new ArrayList<>();
        for (final MethodCandidate candidate : callableMethods.producing(targetType).collect(
                java.util.stream.Collectors.toUnmodifiableList())) {
            final ExecutableElement method = candidate.getMethod();
            final List<? extends javax.lang.model.element.VariableElement> params = method.getParameters();
            if (params.size() != 1) {
                continue;
            }
            final TypeMirror paramType = params.get(0).asType();
            if (!ctx.types().isAssignable(sourceType, paramType)) {
                continue;
            }
            final TypeMirror returnType = method.getReturnType();
            if (!ctx.types().isAssignable(returnType, targetType)) {
                continue;
            }
            final int paramDistance = subtypeDistance(sourceType, paramType, ctx);
            final int returnDistance = subtypeDistance(returnType, targetType, ctx);
            final int weight = Weights.METHOD + paramDistance + returnDistance;
            final EdgeCodegen codegen = renderCodegen(candidate);
            steps.add(new BridgeStep(paramType, returnType, weight, codegen));
        }
        return steps.stream();
    }

    private EdgeCodegen renderCodegen(final MethodCandidate candidate) {
        final CodeBlock receiver = candidate.getReceiver().asExpression();
        final ExecutableElement method = candidate.getMethod();
        final String methodName = method.getSimpleName().toString();
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
            final Pair current = queue.remove(0);
            final TypeMirror currentType = current.type;
            final int depth = current.depth;
            final javax.lang.model.element.Element elem = ctx.types().asElement(currentType);
            if (!(elem instanceof TypeElement)) {
                continue;
            }
            final TypeElement typeElement = (TypeElement) elem;
            final TypeMirror directSupertypes = typeElement.getSuperclass();
            if (directSupertypes == null) {
                continue;
            }
            final String supKey = directSupertypes.toString();
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
