package io.github.joke.percolate.processor.internal.stages.generate;

import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.Operation;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.spi.BodyCodegen;
import io.github.joke.percolate.spi.BodyRenderContext;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.SwitchStyle;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.function.Function;
import javax.lang.model.SourceVersion;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

// Assembles the BodyRenderContext a BodyCodegen producer renders its whole method body against, and decides
// whether that verbatim path applies at all. Split from BodyRenderContextImpl by change tighten-testability-
// conventions (design D2): the dispatch — verbatim body iff the chosen producer carries a BodyCodegen, reading
// no target version and no processor option — is a decision worth intercepting, where BodyRenderContextImpl
// itself is the built context and answers questions about it.
@NoArgsConstructor(onConstructor_ = @Inject)
final class BodyRenderContextFactory {

    // Renders producer's codegen when it is a BodyCodegen — the whole method body, verbatim — else empty (an
    // OperationCodegen producer, or no chosen producer at all).
    @VisibleForTesting
    Optional<CodeBlock> renderIfBodyCodegen(
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

    // Builds the render context for operation's BodyCodegen, rendering each port via operandRenderer.
    @VisibleForTesting
    BodyRenderContext buildFor(
            final MapperGraph graph,
            final Operation operation,
            final Function<Value, CodeBlock> operandRenderer,
            final MemberPlan memberPlan,
            final ResolveCtx resolveCtx,
            final SwitchStyle switchStyle,
            final SourceVersion sourceVersion) {
        final var positional = new ArrayList<CodeBlock>();
        final var byName = new LinkedHashMap<String, CodeBlock>();
        final var portTypes = new LinkedHashMap<String, TypeMirror>();
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
        final var members = new LinkedHashMap<String, CodeBlock>();
        operation
                .getMemberRequests()
                .forEach(request -> members.put(request.getDedupKey(), memberPlan.reference(request.getDedupKey())));
        final var incoming = new IncomingValuesImpl(positional, byName, members);
        return new BodyRenderContextImpl(incoming, portTypes, resolveCtx, switchStyle, sourceVersion);
    }
}
