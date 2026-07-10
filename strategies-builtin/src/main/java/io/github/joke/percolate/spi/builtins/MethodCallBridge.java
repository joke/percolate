package io.github.joke.percolate.spi.builtins;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.MethodCandidate;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.stream.Stream;
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

    private final SubtypeDistance subtypeDistance = new SubtypeDistance();

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        if (!demand.declaredChildren().isEmpty()) {
            // A target the user declared field-by-field is assembled, not produced by a method call: a method-call
            // bridge applies only to leaf demands. (Degenerate self-calls are refused at bind time by the driver
            // via the spec's call target, independently of this leaf-only constraint.)
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
                            && ctx.isAssignable(method.getReturnType(), targetType);
                })
                .map(candidate -> buildSpec(candidate, targetType, demand, ctx));
    }

    OperationSpec buildSpec(
            final MethodCandidate candidate,
            final TypeMirror targetType,
            final ProduceDemand demand,
            final ResolveCtx ctx) {
        final var method = candidate.getMethod();
        final var param = method.getParameters().get(0);
        final var returnType = method.getReturnType();
        final var returnDistance = subtypeDistance.between(returnType, targetType, ctx);
        final var weight = Weights.METHOD + returnDistance;
        final var port =
                new Port(param.getSimpleName().toString(), param.asType(), demand.nullnessOf(param.asType(), param));
        return OperationSpec.callOf(
                method.getSimpleName() + "(…)",
                renderCodegen(candidate),
                weight,
                List.of(port),
                returnType,
                demand.nullnessOf(returnType, method),
                method);
    }

    OperationCodegen renderCodegen(final MethodCandidate candidate) {
        final var receiver = candidate.getReceiver().asExpression();
        final var method = candidate.getMethod();
        final var methodName = method.getSimpleName().toString();
        return inputs -> CodeBlock.of("$L$Z.$N($L)", receiver, methodName, inputs.single());
    }
}
