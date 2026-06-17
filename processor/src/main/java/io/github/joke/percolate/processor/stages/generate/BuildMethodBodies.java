package io.github.joke.percolate.processor.stages.generate;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.ExtractedPlan;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.graph.Operation;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.Value;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.ScopeCodegen;
import jakarta.inject.Inject;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import lombok.RequiredArgsConstructor;

/**
 * Composes each abstract method body by walking the {@link ExtractedPlan} from the method's return-root
 * {@link Value} (design D8/codegen). Each in-plan Value's chosen producer {@link Operation} is rendered by
 * invoking its codegen with {@link io.github.joke.percolate.spi.IncomingValues} keyed by port name; a leaf
 * (a supply root) renders the parameter or the element lambda variable. A scope-owning Operation (container
 * element mapping) weaves its container codegen around the child plan rendered as a lambda body. Producer
 * identity is structural — no group, label, or shared-codegen inference — and no nullability is read (crossings
 * are ordinary plan Operations).
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class BuildMethodBodies {

    List<MethodImpl> build(final MapperContext ctx) {
        final var shape = ctx.getShape();
        final var graph = ctx.getGraph();
        if (shape == null || graph == null) {
            return List.of();
        }
        final var plan = ExtractedPlan.extract(graph);
        return shape.getAbstractMethods().stream()
                .map(method -> renderMethod(graph, plan, method))
                .collect(toUnmodifiableList());
    }

    private MethodImpl renderMethod(final MapperGraph graph, final ExtractedPlan plan, final ExecutableElement method) {
        final var root = returnRoot(graph, new MethodScope(method));
        final var expression = new Walk(graph, plan).render(root);
        final var body =
                CodeBlock.builder().addStatement("return $L", expression).build();
        return new MethodImpl(method, body, Set.of());
    }

    private static Value returnRoot(final MapperGraph graph, final Scope scope) {
        return graph.valuesIn(scope)
                .filter(value -> value.getLoc().isReturnRoot())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no return-root Value in scope " + scope.encode()));
    }

    /** One method-body render: holds the graph, the plan, the lambda-variable environment, and the var generator. */
    private static final class Walk {

        private final MapperGraph graph;
        private final ExtractedPlan plan;

        @SuppressWarnings({"PMD.UseConcurrentHashMap", "IdentityHashMapUsage"})
        private final Map<Value, CodeBlock> lambdaVars = new IdentityHashMap<>();

        private int nextVar;

        private Walk(final MapperGraph graph, final ExtractedPlan plan) {
            this.graph = graph;
            this.plan = plan;
        }

        private CodeBlock render(final Value value) {
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
        private CodeBlock renderPlain(final Operation operation) {
            final List<CodeBlock> positional = new java.util.ArrayList<>();
            final Map<String, CodeBlock> byName = new LinkedHashMap<>();
            for (final var port : operation.getPorts()) {
                final var operand = graph.portSource(operation, port.getName())
                        .map(this::render)
                        .orElseThrow(
                                () -> new IllegalStateException("operation port has no source: " + port.getName()));
                positional.add(operand);
                byName.put(port.getName(), operand);
            }
            return ((OperationCodegen) operation.getCodegen()).render(new IncomingValuesImpl(positional, byName));
        }

        private CodeBlock renderContainerMapping(final Operation operation) {
            final var sourcePort = operation.getPorts().get(0);
            final var sourceExpr = graph.portSource(operation, sourcePort.getName())
                    .map(this::render)
                    .orElseThrow(() -> new IllegalStateException("container mapping has no source port"));
            final var child = operation.getChildScope().orElseThrow();
            final var var = freshVar();
            lambdaVars.put(child.getParamRoot(), CodeBlock.of("$N", var));
            final var childBody = render(child.getReturnRoot());
            return ((ScopeCodegen) operation.getCodegen()).weave(sourceExpr, var, childBody);
        }

        private CodeBlock renderLeaf(final Value value) {
            final var bound = lambdaVars.get(value);
            if (bound != null) {
                return bound;
            }
            if (value.getLoc() instanceof SourceLocation) {
                final var segments = ((SourceLocation) value.getLoc()).getPath().getSegments();
                if (!segments.isEmpty()) {
                    return CodeBlock.of("$N", segments.get(0));
                }
            }
            throw new IllegalStateException("unproducible leaf Value in plan: " + value.id());
        }

        private String freshVar() {
            final var current = nextVar;
            nextVar++;
            return "v" + current;
        }
    }
}
