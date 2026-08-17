package io.github.joke.percolate.processor.internal.stages.generate;

import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.lib.javapoet.TypeName;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.ProcessorOptions;
import io.github.joke.percolate.processor.internal.graph.ChildScope;
import io.github.joke.percolate.processor.internal.graph.ElementLocation;
import io.github.joke.percolate.processor.internal.graph.ExtractedPlan;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.MethodScope;
import io.github.joke.percolate.processor.internal.graph.Operation;
import io.github.joke.percolate.processor.internal.graph.SourceLocation;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.ScopeCodegen;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.processor.internal.graph.ExtractedPlan.extract;
import static java.util.stream.Collectors.toUnmodifiableList;

// Composes each abstract method body by walking the ExtractedPlan from the method's return-root Value (design
// D8/codegen). Each scope renders as an ordered list of local-variable declarations followed by a single result
// expression: a plan Value is hoisted to a local (per HoistPlan — assembly arguments and shared Values) and
// referenced by name, while single-port chains and the return-root render inline, so fluent container pipelines
// stay one threaded chain. A chosen producer is rendered by invoking its codegen with
// io.github.joke.percolate.spi.IncomingValues keyed by port name; a leaf (a supply root) renders the parameter
// or the element lambda variable. A scope-owning Operation (container element mapping) weaves its container
// codegen around the child scope rendered as a lambda — an expression lambda when the child hoists nothing, a
// block lambda when it does. Producer identity is structural — no group, label, or shared-codegen inference —
// and no nullability is read (crossings are ordinary plan Operations).
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class BuildMethodBodies {

    private final ProcessorOptions options;
    private final SourceVersion sourceVersion;
    private final HoistPlanFactory hoistPlanFactory;
    private final MemberPlanFactory memberPlanFactory;
    private final BodyRenderContextFactory bodyRenderContextFactory;

    @VisibleForTesting
    MethodBodies build(final MapperContext ctx) {
        final var shape = ctx.getShape();
        final var graph = ctx.getGraph();
        final var resolveCtx = ctx.getResolveCtx();
        if (shape == null || graph == null || resolveCtx == null) {
            return new MethodBodies(List.of(), List.of());
        }
        final var plan = extract(graph);
        final var memberPlan = memberPlanFactory.forMapper(graph, plan, ctx);
        final var bodies = shape.getAbstractMethods().stream()
                .map(method -> renderMethod(graph, plan, memberPlan, method, resolveCtx))
                .collect(toUnmodifiableList());
        return new MethodBodies(bodies, memberPlan.fields());
    }

    @VisibleForTesting
    MethodImpl renderMethod(
            final MapperGraph graph,
            final ExtractedPlan plan,
            final MemberPlan memberPlan,
            final ExecutableElement method,
            final ResolveCtx resolveCtx) {
        final var root = graph.returnRootIn(new MethodScope(method));
        final var reserved = method.getParameters().stream()
                .map(parameter -> parameter.getSimpleName().toString())
                .collect(toUnmodifiableList());
        final var hoist = hoistPlanFactory.forMethod(graph, plan, root, reserved);
        final var style = new LocalStyle(options.isLocalsFinal(), options.isLocalsVar());
        final var body = new Walk(
                        graph,
                        plan,
                        hoist,
                        memberPlan,
                        style,
                        new TypeNameRenderer(),
                        resolveCtx,
                        sourceVersion,
                        bodyRenderContextFactory)
                .renderMethodBody(root);
        return new MethodImpl(method, body, Set.of());
    }

    // One method-body render (decomposed by change decompose-engine-stages): holds the graph, the plan, the hoist
    // decision, the lambda-variable environment, and the injected TypeNameRenderer — the sole compiler-backed leaf,
    // so every other method here is pure assembly logic a spec can mock/spy in isolation. Package-visible so the
    // unit suite drives it directly; production code reaches it only through .renderMethod. Its own methods recurse
    // into one another over the plan's structure (renderInline ↔
    // renderOperand/renderContainerMapping/renderScopeBody), so a spec isolating one of them spies the subject and
    // stubs the recursive call, per the Grounding precedent (design D5).
    static final class Walk {

        private final MapperGraph graph;
        private final ExtractedPlan plan;
        private final HoistPlan hoist;
        private final MemberPlan memberPlan;
        private final LocalStyle style;
        private final TypeNameRenderer typeNameRenderer;
        private final ResolveCtx resolveCtx;
        private final SourceVersion sourceVersion;
        private final BodyRenderContextFactory bodyRenderContextFactory;

        @SuppressWarnings({"PMD.UseConcurrentHashMap", "IdentityHashMapUsage"})
        private final Map<Value, CodeBlock> lambdaVars = new IdentityHashMap<>();

        // Every parameter is per-render state the Walk reads directly. It sat one over PMD's ceiling until the
        // switch.style option moved onto the generic ResolveCtx.option(…) seam and took a parameter with it.
        Walk(
                final MapperGraph graph,
                final ExtractedPlan plan,
                final HoistPlan hoist,
                final MemberPlan memberPlan,
                final LocalStyle style,
                final TypeNameRenderer typeNameRenderer,
                final ResolveCtx resolveCtx,
                final SourceVersion sourceVersion,
                final BodyRenderContextFactory bodyRenderContextFactory) {
            this.graph = graph;
            this.plan = plan;
            this.hoist = hoist;
            this.memberPlan = memberPlan;
            this.style = style;
            this.typeNameRenderer = typeNameRenderer;
            this.resolveCtx = resolveCtx;
            this.sourceVersion = sourceVersion;
            this.bodyRenderContextFactory = bodyRenderContextFactory;
        }

        // The method body: when the return-root's chosen producer carries a BodyCodegen, its complete body renders
        // verbatim (no enclosing return <expr>;) — dispatch is solely on which codegen shape the producer supplied,
        // reading no target Java version and no processor option here. Otherwise the scope's local declarations, then
        // return <return-root expression>;.
        @VisibleForTesting
        CodeBlock renderMethodBody(final Value root) {
            final var bodyRendered = bodyRenderContextFactory.renderIfBodyCodegen(
                    graph, plan.chosenProducer(root), this::renderOperand, memberPlan, resolveCtx, sourceVersion);
            if (bodyRendered.isPresent()) {
                return bodyRendered.get();
            }
            final var builder = CodeBlock.builder();
            for (final var value : hoistedInScope(root)) {
                emitLocal(builder, value);
            }
            return builder.addStatement("return $L", renderInline(root)).build();
        }

        // A child (lambda) scope body: the inline expression when it hoists nothing (an expression lambda stays terse),
        // otherwise a {@code { <decls>; return <expr>; }} block (a block lambda).
        @VisibleForTesting
        CodeBlock renderScopeBody(final Value root) {
            final var hoistedHere = hoistedInScope(root);
            if (hoistedHere.isEmpty()) {
                return renderInline(root);
            }
            final var builder = CodeBlock.builder().add("{\n").indent();
            for (final var value : hoistedHere) {
                emitLocal(builder, value);
            }
            return builder.addStatement("return $L", renderInline(root))
                    .unindent()
                    .add("}")
                    .build();
        }

        // Emit one hoisted local: [final] <Type|var> <name> = <expr>; per the configured LocalStyle.
        @VisibleForTesting
        void emitLocal(final CodeBlock.Builder builder, final Value value) {
            final var name = hoist.declare(value);
            final var rhs = renderInline(value);
            builder.addStatement("$L$L $N = $L", style.isMakeFinal() ? "final " : "", typeToken(value), name, rhs);
        }

        // The declaration's type token: var when configured, otherwise the Value's rendered type.
        @VisibleForTesting
        CodeBlock typeToken(final Value value) {
            return style.isUseVar() ? CodeBlock.of("var") : CodeBlock.of("$T", localType(value));
        }

        // An operand: a variable reference when the Value is hoisted, otherwise its inline expression.
        @VisibleForTesting
        CodeBlock renderOperand(final Value value) {
            return hoist.isHoisted(value) ? hoist.reference(value) : renderInline(value);
        }

        // The inline expression for a Value: its chosen producer's rendering, or the leaf name.
        @VisibleForTesting
        CodeBlock renderInline(final Value value) {
            final var producer = plan.chosenProducer(value);
            if (producer.isEmpty()) {
                return renderLeaf(value);
            }
            final var operation = producer.get();
            if (operation.getChildScope().isPresent()) {
                return renderContainerMapping(operation);
            }
            return renderPlain(operation);
        }

        @VisibleForTesting
        CodeBlock renderPlain(final Operation operation) {
            final var positional = new ArrayList<CodeBlock>();
            final var byName = new LinkedHashMap<String, CodeBlock>();
            for (final var port : operation.getPorts()) {
                final var operand = graph.portSource(operation, port.getName())
                        .map(this::renderOperand)
                        .orElseThrow(
                                () -> new IllegalStateException("operation port has no source: " + port.getName()));
                positional.add(operand);
                byName.put(port.getName(), operand);
            }
            final var members = new LinkedHashMap<String, CodeBlock>();
            operation
                    .getMemberRequests()
                    .forEach(
                            request -> members.put(request.getDedupKey(), memberPlan.reference(request.getDedupKey())));
            return ((OperationCodegen) operation.getCodegen())
                    .render(new IncomingValuesImpl(positional, byName, members));
        }

        @VisibleForTesting
        CodeBlock renderContainerMapping(final Operation operation) {
            final var sourcePort = operation.getPorts().get(0);
            final var sourceExpr = graph.portSource(operation, sourcePort.getName())
                    .map(this::renderOperand)
                    .orElseThrow(() -> new IllegalStateException("container mapping has no source port"));
            final var child = operation.getChildScope().orElseThrow();
            final var var = hoist.lambdaName(child.getElementInput().getType());
            materialisedElementRoot(child).ifPresent(paramRoot -> lambdaVars.put(paramRoot, CodeBlock.of("$N", var)));
            final var childBody = renderScopeBody(child.getReturnRoot());
            return ((ScopeCodegen) operation.getCodegen()).weave(sourceExpr, var, childBody);
        }

        // The element param-root Value if the child plan sourced from it (lazily materialised), else empty.
        @VisibleForTesting
        Optional<Value> materialisedElementRoot(final ChildScope child) {
            return graph.valuesIn(child)
                    .filter(value -> value.getLoc() instanceof ElementLocation)
                    .findFirst();
        }

        @VisibleForTesting
        CodeBlock renderLeaf(final Value value) {
            final var bound = lambdaVars.get(value);
            if (bound != null) {
                return bound;
            }
            return sourceSegmentRoot(value)
                    .orElseThrow(() -> new IllegalStateException("unproducible leaf Value in plan: " + value.id()));
        }

        // value's first source-path segment, rendered as a bare reference, or empty when it has none.
        @VisibleForTesting
        Optional<CodeBlock> sourceSegmentRoot(final Value value) {
            if (!(value.getLoc() instanceof SourceLocation)) {
                return Optional.empty();
            }
            final var segments = ((SourceLocation) value.getLoc()).getPath().getSegments();
            return segments.isEmpty() ? Optional.empty() : Optional.of(CodeBlock.of("$N", segments.get(0)));
        }

        // The declared type of a hoisted local, rendered through the injected TypeNameRenderer.
        @VisibleForTesting
        TypeName localType(final Value value) {
            return typeNameRenderer.render(value.getType()
                    .orElseThrow(() -> new IllegalStateException("hoisted Value has no type: " + value.id())));
        }

        // The hoisted Values of root's scope in dependency (post-order) order, so each local precedes its first
        // reference. The walk stays within the scope — it descends a producer's port sources but never its child scope
        // — and excludes root itself (the return-root renders inline).
        @VisibleForTesting
        List<Value> hoistedInScope(final Value root) {
            final var ordered = new ArrayList<Value>();
            // Value is identity-equal (equals/hashCode are identity), so a HashSet is effectively an identity set.
            collectHoisted(root, root, ordered, new HashSet<>());
            return ordered;
        }

        @VisibleForTesting
        void collectHoisted(final Value value, final Value root, final List<Value> ordered, final Set<Value> seen) {
            if (!seen.add(value)) {
                return;
            }
            final var producer = plan.chosenProducer(value);
            if (producer.isEmpty()) {
                return;
            }
            descendAndRecord(value, root, ordered, seen, producer.get());
        }

        @VisibleForTesting
        void descendAndRecord(
                final Value value,
                final Value root,
                final List<Value> ordered,
                final Set<Value> seen,
                final Operation producer) {
            graph.portSourcesOf(producer).forEach(source -> collectHoisted(source, root, ordered, seen));
            if (!value.equals(root) && hoist.isHoisted(value)) {
                ordered.add(value);
            }
        }
    }
}
