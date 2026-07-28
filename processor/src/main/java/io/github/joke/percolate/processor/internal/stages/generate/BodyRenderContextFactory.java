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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.lang.model.SourceVersion;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

// Assembles the BodyRenderContext a BodyCodegen producer renders its whole method body against, and decides
// whether that verbatim path applies at all. Split from BodyRenderContextImpl by change tighten-testability-
// conventions (design D2): the dispatch — verbatim body iff the chosen producer carries a BodyCodegen, reading
// no target version and no processor option — is a decision worth intercepting, where BodyRenderContextImpl
// itself is the built context and answers questions about it.
@NoArgsConstructor(onConstructor_ = @Inject)
final class BodyRenderContextFactory {

    // Renders producer's codegen when it is a BodyCodegen — the whole method body, verbatim — else empty (an
    // OperationCodegen producer, or no chosen producer at all).
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
    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded render; insertion order matters
    BodyRenderContext buildFor(
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
}
