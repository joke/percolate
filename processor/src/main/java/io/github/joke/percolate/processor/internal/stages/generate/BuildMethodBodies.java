package io.github.joke.percolate.processor.internal.stages.generate;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
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
import io.github.joke.percolate.spi.DocTags;
import io.github.joke.percolate.spi.OperationCodegen;
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
import javax.lang.model.element.ExecutableElement;
import lombok.RequiredArgsConstructor;

/**
 * Composes each abstract method body by walking the {@link ExtractedPlan} from the method's return-root
 * {@link Value} (design D8/codegen). Each <b>scope</b> renders as an ordered list of local-variable declarations
 * followed by a single result expression: a plan Value is hoisted to a local (per {@link HoistPlan} — assembly
 * arguments and shared Values) and referenced by name, while single-port chains and the return-root render inline,
 * so fluent container pipelines stay one threaded chain. A chosen producer is rendered by invoking its codegen
 * with {@link io.github.joke.percolate.spi.IncomingValues} keyed by port name; a leaf (a supply root) renders the
 * parameter or the element lambda variable. A scope-owning Operation (container element mapping) weaves its
 * container codegen around the child scope rendered as a lambda — an expression lambda when the child hoists
 * nothing, a block lambda when it does. Producer identity is structural — no group, label, or shared-codegen
 * inference — and no nullability is read (crossings are ordinary plan Operations).
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class BuildMethodBodies {

    private final ProcessorOptions options;

    MethodBodies build(final MapperContext ctx) {
        final var shape = ctx.getShape();
        final var graph = ctx.getGraph();
        if (shape == null || graph == null) {
            return new MethodBodies(List.of(), List.of());
        }
        final var plan = ExtractedPlan.extract(graph);
        final var memberPlan = MemberPlan.forMapper(graph, plan);
        final var bodies = shape.getAbstractMethods().stream()
                .map(method -> renderMethod(graph, plan, memberPlan, method))
                .collect(toUnmodifiableList());
        return new MethodBodies(bodies, memberPlan.fields());
    }

    MethodImpl renderMethod(
            final MapperGraph graph,
            final ExtractedPlan plan,
            final MemberPlan memberPlan,
            final ExecutableElement method) {
        final var root = graph.returnRootIn(new MethodScope(method));
        final var reserved = method.getParameters().stream()
                .map(parameter -> parameter.getSimpleName().toString())
                .collect(toUnmodifiableList());
        final var hoist = HoistPlan.forMethod(graph, plan, root, reserved);
        final var style = new LocalStyle(options.isLocalsFinal(), options.isLocalsVar());
        final var body = new Walk(graph, plan, hoist, memberPlan, style, new TypeNameRenderer()).renderMethodBody(root);
        return new MethodImpl(method, docTagged(body, method), Set.of());
    }

    /**
     * When the {@code docTags} option is on, bracket the method body with AsciiDoc include-tag comments named after
     * the method (the pure tag-wrapping lives in {@link DocTags}), so a documentation build can
     * {@code include::[tag=<method>]} the generated body. Off by default, so ordinary consumer output is
     * byte-for-byte unchanged.
     */
    CodeBlock docTagged(final CodeBlock body, final ExecutableElement method) {
        if (!options.isDocTags()) {
            return body;
        }
        return DocTags.wrap(body, method.getSimpleName().toString());
    }

    /**
     * One method-body render (decomposed by change {@code decompose-engine-stages}): holds the graph, the plan, the
     * hoist decision, the lambda-variable environment, and the injected {@link TypeNameRenderer} — the sole
     * compiler-backed leaf, so every other method here is pure assembly logic a spec can mock/spy in isolation.
     * Package-visible so the unit suite drives it directly; production code reaches it only through
     * {@link #renderMethod}. Its own methods recurse into one another over the plan's structure ({@code renderInline}
     * ↔ {@code renderOperand}/{@code renderContainerMapping}/{@code renderScopeBody}), so a spec isolating one of
     * them spies the subject and stubs the recursive call, per the {@code Grounding} precedent (design D5).
     */
    static final class Walk {

        private final MapperGraph graph;
        private final ExtractedPlan plan;
        private final HoistPlan hoist;
        private final MemberPlan memberPlan;
        private final LocalStyle style;
        private final TypeNameRenderer typeNameRenderer;

        @SuppressWarnings({"PMD.UseConcurrentHashMap", "IdentityHashMapUsage"})
        private final Map<Value, CodeBlock> lambdaVars = new IdentityHashMap<>();

        Walk(
                final MapperGraph graph,
                final ExtractedPlan plan,
                final HoistPlan hoist,
                final MemberPlan memberPlan,
                final LocalStyle style,
                final TypeNameRenderer typeNameRenderer) {
            this.graph = graph;
            this.plan = plan;
            this.hoist = hoist;
            this.memberPlan = memberPlan;
            this.style = style;
            this.typeNameRenderer = typeNameRenderer;
        }

        /** The method body: the scope's local declarations, then {@code return <return-root expression>;}. */
        CodeBlock renderMethodBody(final Value root) {
            final var builder = CodeBlock.builder();
            for (final var value : hoistedInScope(root)) {
                emitLocal(builder, value);
            }
            return builder.addStatement("return $L", renderInline(root)).build();
        }

        /**
         * A child (lambda) scope body: the inline expression when it hoists nothing (an expression lambda stays
         * terse), otherwise a {@code { <decls>; return <expr>; }} block (a block lambda).
         */
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

        /** Emit one hoisted local: {@code [final] <Type|var> <name> = <expr>;} per the configured {@link LocalStyle}. */
        void emitLocal(final CodeBlock.Builder builder, final Value value) {
            final var name = hoist.declare(value);
            final var rhs = renderInline(value);
            builder.addStatement("$L$L $N = $L", style.isMakeFinal() ? "final " : "", typeToken(value), name, rhs);
        }

        /** The declaration's type token: {@code var} when configured, otherwise the Value's rendered type. */
        CodeBlock typeToken(final Value value) {
            return style.isUseVar() ? CodeBlock.of("var") : CodeBlock.of("$T", localType(value));
        }

        /** An operand: a variable reference when the Value is hoisted, otherwise its inline expression. */
        CodeBlock renderOperand(final Value value) {
            return hoist.isHoisted(value) ? hoist.reference(value) : renderInline(value);
        }

        /** The inline expression for a Value: its chosen producer's rendering, or the leaf name. */
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

        @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded render; insertion order is the port order
        CodeBlock renderPlain(final Operation operation) {
            final List<CodeBlock> positional = new ArrayList<>();
            final Map<String, CodeBlock> byName = new LinkedHashMap<>();
            for (final var port : operation.getPorts()) {
                final var operand = graph.portSource(operation, port.getName())
                        .map(this::renderOperand)
                        .orElseThrow(
                                () -> new IllegalStateException("operation port has no source: " + port.getName()));
                positional.add(operand);
                byName.put(port.getName(), operand);
            }
            final Map<String, CodeBlock> members = new LinkedHashMap<>();
            operation
                    .getMemberRequests()
                    .forEach(
                            request -> members.put(request.getDedupKey(), memberPlan.reference(request.getDedupKey())));
            return ((OperationCodegen) operation.getCodegen())
                    .render(new IncomingValuesImpl(positional, byName, members));
        }

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

        /** The element param-root Value if the child plan sourced from it (lazily materialised), else empty. */
        Optional<Value> materialisedElementRoot(final ChildScope child) {
            return graph.valuesIn(child)
                    .filter(value -> value.getLoc() instanceof ElementLocation)
                    .findFirst();
        }

        CodeBlock renderLeaf(final Value value) {
            final var bound = lambdaVars.get(value);
            if (bound != null) {
                return bound;
            }
            return sourceSegmentRoot(value)
                    .orElseThrow(() -> new IllegalStateException("unproducible leaf Value in plan: " + value.id()));
        }

        /** {@code value}'s first source-path segment, rendered as a bare reference, or empty when it has none. */
        Optional<CodeBlock> sourceSegmentRoot(final Value value) {
            if (!(value.getLoc() instanceof SourceLocation)) {
                return Optional.empty();
            }
            final var segments = ((SourceLocation) value.getLoc()).getPath().getSegments();
            return segments.isEmpty() ? Optional.empty() : Optional.of(CodeBlock.of("$N", segments.get(0)));
        }

        /** The declared type of a hoisted local, rendered through the injected {@link TypeNameRenderer}. */
        TypeName localType(final Value value) {
            return typeNameRenderer.render(value.getType()
                    .orElseThrow(() -> new IllegalStateException("hoisted Value has no type: " + value.id())));
        }

        /**
         * The hoisted Values of {@code root}'s scope in dependency (post-order) order, so each local precedes its
         * first reference. The walk stays within the scope — it descends a producer's port sources but never its
         * child scope — and excludes {@code root} itself (the return-root renders inline).
         */
        List<Value> hoistedInScope(final Value root) {
            final List<Value> ordered = new ArrayList<>();
            // Value is identity-equal (equals/hashCode are identity), so a HashSet is effectively an identity set.
            collectHoisted(root, root, ordered, new HashSet<>());
            return ordered;
        }

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
