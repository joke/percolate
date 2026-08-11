package io.github.joke.percolate.spi.builtins.methodcall;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.Ambient;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.IncomingValues;
import io.github.joke.percolate.spi.MethodCandidate;
import io.github.joke.percolate.spi.Offer;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.spi.OperationSpec.callOf;
import static io.github.joke.percolate.spi.Weights.METHOD;
import static java.util.stream.Collectors.toUnmodifiableList;

// Produces the demanded type by calling a callable method that returns it: an OperationSpec carrying one port
// per declared parameter, in declaration order — the single non-ambient parameter's port sourced as today, and
// each @Ambient parameter's port a BY_NAME/REQUIRE port carrying its binding name (design ambient-parameters of
// change decouple-engine-from-strategy-semantics: the strategy reads @Ambient itself — it is SPI-side — rather
// than asking the type-query seam). The strategy stays myopic: it stamps the selector, on-miss rule and binding
// name only, never resolving the scope's named inputs or touching the graph. The operation renders
// receiver.method(arg0, arg1, …), each argument rendered positionally by port name.
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class MethodCallBridge implements ExpansionStrategy {

    private static final int NON_AMBIENT_PARAM_COUNT = 1;

    private final SubtypeDistance subtypeDistance = new SubtypeDistance();

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
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
                .filter(candidate -> isBridgeable(candidate, targetType, ctx))
                .map(candidate -> buildSpec(candidate, targetType, demand, ctx))
                .map(Offer::of);
    }

    // A candidate bridges when exactly one of its parameters is non-ambient and its return type feeds the target.
    @VisibleForTesting
    boolean isBridgeable(final MethodCandidate candidate, final TypeMirror targetType, final ResolveCtx ctx) {
        final var method = candidate.getMethod();
        return nonAmbientParameterCount(method) == NON_AMBIENT_PARAM_COUNT
                && ctx.isAssignable(method.getReturnType(), targetType);
    }

    @VisibleForTesting
    long nonAmbientParameterCount(final ExecutableElement method) {
        return method.getParameters().stream()
                .filter(param -> ambientKey(param).isEmpty())
                .count();
    }

    // The binding key @Ambient publishes for param: its own name, or the annotation's override.
    @VisibleForTesting
    Optional<String> ambientKey(final VariableElement param) {
        final var ambient = param.getAnnotation(Ambient.class);
        if (ambient == null) {
            return Optional.empty();
        }
        return Optional.of(ambient.value().isEmpty() ? param.getSimpleName().toString() : ambient.value());
    }

    @VisibleForTesting
    OperationSpec buildSpec(
            final MethodCandidate candidate,
            final TypeMirror targetType,
            final ProduceDemand demand,
            final ResolveCtx ctx) {
        final var method = candidate.getMethod();
        final var returnType = method.getReturnType();
        final var returnDistance = subtypeDistance.between(returnType, targetType, ctx);
        final var weight = METHOD + returnDistance;
        final var ports = method.getParameters().stream()
                .map(param -> portFor(param, demand, ctx))
                .collect(toUnmodifiableList());
        return callOf(
                method.getSimpleName() + "(…)",
                renderCodegen(candidate, ports),
                weight,
                ports,
                returnType,
                demand.nullnessOf(returnType, method),
                method);
    }

    @VisibleForTesting
    Port portFor(final VariableElement param, final ProduceDemand demand, final ResolveCtx ctx) {
        final var name = param.getSimpleName().toString();
        final var type = param.asType();
        final var nullness = demand.nullnessOf(type, param);
        return ambientKey(param)
                .map(key -> Port.byName(name, type, nullness, key))
                .orElseGet(() -> new Port(name, type, nullness));
    }

    @VisibleForTesting
    OperationCodegen renderCodegen(final MethodCandidate candidate, final List<Port> ports) {
        final var receiver = candidate.getReceiver().asExpression();
        final var method = candidate.getMethod();
        final var methodName = method.getSimpleName().toString();
        final var portNames = ports.stream().map(Port::getName).collect(toUnmodifiableList());
        return inputs -> renderCall(receiver, methodName, portNames, inputs);
    }

    // CodeBlock.joining is JavaPoet's own collector, not Collectors.joining, which is already static-imported
    // here under the same simple name.
    @SuppressWarnings("PMD.UseStaticImports")
    @VisibleForTesting
    CodeBlock renderCall(
            final CodeBlock receiver,
            final String methodName,
            final List<String> portNames,
            final IncomingValues inputs) {
        final var args = portNames.stream().map(inputs::byName).collect(CodeBlock.joining(", "));
        return CodeBlock.of("$L$Z.$N($L)", receiver, methodName, args);
    }
}
