package io.github.joke.percolate.processor.internal.stages.generate;

import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.Operation;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.spi.BodyCodegen;
import io.github.joke.percolate.spi.BodyRenderContext;
import io.github.joke.percolate.spi.IncomingValues;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.SwitchStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.lang.model.SourceVersion;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

/**
 * The {@link BodyRenderContext} runtime implementation: a superset of {@link IncomingValues} (delegated to a
 * composed {@link IncomingValuesImpl}) that additionally exposes, per port, the grounded concrete {@link TypeMirror}
 * bound to that port, the per-mapper {@link ResolveCtx}, the effective {@link SwitchStyle}, and the target
 * {@link SourceVersion}. {@link #buildFor} gathers a {@code BodyCodegen} operation's port operands/types the same
 * way {@code BuildMethodBodies.Walk#renderPlain} gathers an {@code OperationCodegen}'s, so the two codegen shapes
 * see consistent port data.
 */
@RequiredArgsConstructor
// each field backs the BodyRenderContext accessor of the same name
@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
final class BodyRenderContextImpl implements BodyRenderContext {

    private final IncomingValues incomingValues;
    private final Map<String, TypeMirror> portTypes;
    private final ResolveCtx resolveCtx;
    private final SwitchStyle switchStyle;
    private final SourceVersion sourceVersion;

    /**
     * Renders {@code producer}'s codegen when it is a {@link BodyCodegen} — the whole method body, verbatim — else
     * empty (an {@link OperationCodegen} producer, or no chosen producer at all).
     */
    static Optional<CodeBlock> renderIfBodyCodegen(
            final MapperGraph graph,
            final Optional<Operation> producer,
            final Function<Value, CodeBlock> operandRenderer,
            final MemberPlan memberPlan,
            final ResolveCtx resolveCtx,
            final SwitchStyle switchStyle,
            final SourceVersion sourceVersion) {
        if (producer.isEmpty()) {
            return Optional.empty();
        }
        final var operation = producer.get();
        final var codegen = operation.getCodegen();
        if (!(codegen instanceof BodyCodegen)) {
            return Optional.empty();
        }
        final var context =
                buildFor(graph, operation, operandRenderer, memberPlan, resolveCtx, switchStyle, sourceVersion);
        return Optional.of(((BodyCodegen) codegen).render(context));
    }

    /** Builds the render context for {@code operation}'s {@code BodyCodegen}, rendering each port via {@code operandRenderer}. */
    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded render; insertion order matters
    static BodyRenderContext buildFor(
            final MapperGraph graph,
            final Operation operation,
            final Function<Value, CodeBlock> operandRenderer,
            final MemberPlan memberPlan,
            final ResolveCtx resolveCtx,
            final SwitchStyle switchStyle,
            final SourceVersion sourceVersion) {
        final List<CodeBlock> positional = new ArrayList<>();
        final Map<String, CodeBlock> byName = new LinkedHashMap<>();
        final Map<String, TypeMirror> portTypes = new LinkedHashMap<>();
        for (final var port : operation.getPorts()) {
            final var source = graph.portSource(operation, port.getName())
                    .orElseThrow(() -> new IllegalStateException("operation port has no source: " + port.getName()));
            final var operand = operandRenderer.apply(source);
            positional.add(operand);
            byName.put(port.getName(), operand);
            portTypes.put(
                    port.getName(),
                    source.getType()
                            .orElseThrow(
                                    () -> new IllegalStateException("port source has no type: " + port.getName())));
        }
        final Map<String, CodeBlock> members = new LinkedHashMap<>();
        operation
                .getMemberRequests()
                .forEach(request -> members.put(request.getDedupKey(), memberPlan.reference(request.getDedupKey())));
        final var incoming = new IncomingValuesImpl(positional, byName, members);
        return new BodyRenderContextImpl(incoming, portTypes, resolveCtx, switchStyle, sourceVersion);
    }

    @Override
    public CodeBlock single() {
        return incomingValues.single();
    }

    @Override
    public CodeBlock byGroupPosition(final int idx) {
        return incomingValues.byGroupPosition(idx);
    }

    @Override
    public CodeBlock byName(final String slotName) {
        return incomingValues.byName(slotName);
    }

    @Override
    public CodeBlock member(final String dedupKey) {
        return incomingValues.member(dedupKey);
    }

    @Override
    public TypeMirror portType(final String portName) {
        final var type = portTypes.get(portName);
        if (type == null) {
            throw new IllegalStateException("No grounded type for port: " + portName);
        }
        return type;
    }

    @Override
    public ResolveCtx resolveCtx() {
        return resolveCtx;
    }

    @Override
    public SwitchStyle switchStyle() {
        return switchStyle;
    }

    @Override
    public SourceVersion sourceVersion() {
        return sourceVersion;
    }
}
