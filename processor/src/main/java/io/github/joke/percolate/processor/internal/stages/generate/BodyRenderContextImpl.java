package io.github.joke.percolate.processor.internal.stages.generate;

import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.BodyRenderContext;
import io.github.joke.percolate.spi.IncomingValues;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.SwitchStyle;
import java.util.Map;
import javax.lang.model.SourceVersion;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

// The BodyRenderContext runtime implementation: a superset of IncomingValues (delegated to a composed
// IncomingValuesImpl) that additionally exposes, per port, the grounded concrete TypeMirror bound to that port,
// the per-mapper ResolveCtx, the effective SwitchStyle, and the target SourceVersion.
// BodyRenderContextFactory.buildFor gathers a BodyCodegen operation's port operands/types the same way
// BuildMethodBodies.Walk.renderPlain gathers an OperationCodegen's, so the two codegen shapes see consistent
// port data.
@RequiredArgsConstructor
// each field backs the BodyRenderContext accessor of the same name
@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
final class BodyRenderContextImpl implements BodyRenderContext {

    private final IncomingValues incomingValues;
    private final Map<String, TypeMirror> portTypes;
    private final ResolveCtx resolveCtx;
    private final SwitchStyle switchStyle;
    private final SourceVersion sourceVersion;

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
