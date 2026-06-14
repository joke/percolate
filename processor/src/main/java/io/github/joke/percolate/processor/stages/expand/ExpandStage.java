package io.github.joke.percolate.processor.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.AccessPath;
import io.github.joke.percolate.processor.graph.AddOperation;
import io.github.joke.percolate.processor.graph.AddValue;
import io.github.joke.percolate.processor.graph.ChildScopeDecl;
import io.github.joke.percolate.processor.graph.Location;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Operation;
import io.github.joke.percolate.processor.graph.PortBinding;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.graph.TargetPath;
import io.github.joke.percolate.processor.graph.Value;
import io.github.joke.percolate.processor.model.GoalSpec;
import io.github.joke.percolate.processor.model.MappingDirective;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.processor.stages.Stage;
import io.github.joke.percolate.spi.Candidate;
import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The expansion driver (design D6/D9), a single uniform demand work-list over the bipartite graph: a demanded
 * target {@code Value} asks "what produces this?", the full strategy set answers with {@link OperationSpec}s, and the
 * driver lands each as an atomic {@link AddOperation} whose ports become new demands. There is no per-supply-mode
 * branch (no assembly/bridge split) and the driver builds no {@code Operation} itself — every plan Operation,
 * including nullness crossings and source accessors, originates from a strategy. Misfires are prevented structurally
 * at emission time (assembly gates on the declared-bindings goal spec; conversions on a candidate type match), not by
 * a routing branch.
 *
 * <p>A landed Operation's ports are bound and re-demanded uniformly: a port named after a declared child becomes a
 * deeper child-target demand; any other port reuses an in-scope source Value of the port's type and (assignment-
 * compatible) nullness — preferring the directive-pinned source so a same-typed sibling can never shadow it — or, when
 * none exists, a fresh intermediate at the output location that is itself re-demanded (a multi-hop conversion) and
 * left unreachable if nothing produces it. The directive's source path is materialized on demand by a leaf-first
 * recursive accessor descent that pins the source by {@link SourceLocation}.
 *
 * <p>After the work-list drains the graph is fully over-emitted; satisfaction is not computed here — a vertex is
 * reachable iff its extraction cost is finite ({@code ExtractedPlan}), so there is no separate SAT pass.
 */
@RequiredArgsConstructor
public final class ExpandStage implements Stage {

    private static final int SINGLE_SEGMENT = 1;

    private final List<ExpansionStrategy> strategies;
    private final ResolveCtx resolveCtx;
    private final NullabilityResolver resolver;

    @Override
    public void run(final MapperContext ctx) {
        final var graph = ctx.getGraph();
        if (graph == null) {
            return;
        }
        new Driver(graph, ctx.getGoalSpecs()).expandAll();
    }

    /** One expansion run over a single graph: holds the work-list, the per-Value visited set, and the descent memo. */
    private final class Driver {

        private final MapperGraph graph;
        private final Map<Scope, GoalSpec> goalSpecs;
        private final Applier applier = new Applier();
        private final Deque<Value> workList = new ArrayDeque<>();
        private final Set<Value> visited = new HashSet<>();
        private final Set<String> descended = new HashSet<>();

        private Driver(final MapperGraph graph, final Map<Scope, GoalSpec> goalSpecs) {
            this.graph = graph;
            this.goalSpecs = goalSpecs;
        }

        private void expandAll() {
            graph.values()
                    .filter(value -> value.getLoc().isReturnRoot())
                    .collect(toUnmodifiableList())
                    .forEach(this::enqueue);
            while (!workList.isEmpty()) {
                final var value = workList.poll();
                if (visited.add(value)) {
                    expand(value);
                }
            }
        }

        private void enqueue(final Value value) {
            workList.add(value);
        }

        private void expand(final Value value) {
            if (value.getLoc().role() != Location.Role.DEMAND) {
                return;
            }
            final var scope = value.getScope();
            final var path = ((TargetLocation) value.getLoc()).getPath().toString();
            final var goalSpec = goalSpecs.getOrDefault(scope, GoalSpec.from(List.of()));
            final var children = goalSpec.declaredChildren(path);
            final var binding = goalSpec.bindingFor(path);
            final Optional<Directive> directive = binding.map(BindingDirective::from);
            final var pinnedSource = binding.filter(MappingDirective::hasSource)
                    .map(d -> descend(scope, splitPath(d.getSource())))
                    .orElse(null);
            final var demand = new DemandView(
                    type(value),
                    nullness(value),
                    directive,
                    children,
                    value.getLoc().slotName(),
                    candidates(scope),
                    resolver);
            for (final var spec : dedup(run(demand))) {
                land(value, spec, children, pinnedSource);
            }
        }

        // ---- landing an operation -------------------------------------------------------------------------

        private void land(
                final Value output,
                final OperationSpec spec,
                final Set<String> children,
                final @Nullable Value pinnedSource) {
            final var parentPath = ((TargetLocation) output.getLoc()).getPath().toString();
            final var ports = spec.getPorts().stream()
                    .map(port -> new PortBinding(port, sourceForPort(output, parentPath, port, children, pinnedSource)))
                    .collect(toUnmodifiableList());
            final var operation = apply(new AddOperation(
                    spec.getCodegen().getClass().getSimpleName(),
                    spec.getCodegen().getClass().getName(),
                    spec.getCodegen(),
                    spec.getWeight(),
                    spec.isPartial(),
                    ports,
                    outputOf(output),
                    spec.getChildScope()
                            .map(child -> new ChildScopeDecl(
                                    child.getElementIn(),
                                    child.getElementInNullness(),
                                    child.getElementOut(),
                                    child.getElementOutNullness()))));
            graph.portSourcesOf(operation).forEach(this::enqueue);
            operation.getChildScope().ifPresent(child -> enqueue(child.getReturnRoot()));
        }

        /**
         * Binds one port to a feeding {@code Value} (D1, "a port is a demand"): a declared-child port becomes a
         * deeper child-target demand; otherwise the port reuses the directive-pinned source, then any in-scope source
         * of the port's type and assignment-compatible nullness, then a fresh intermediate at the output location.
         */
        private AddValue sourceForPort(
                final Value output,
                final String parentPath,
                final Port port,
                final Set<String> children,
                final @Nullable Value pinnedSource) {
            if (children.contains(port.getName())) {
                return new AddValue(
                        output.getScope(),
                        childLocation(parentPath, port.getName()),
                        port.getType(),
                        port.getNullness());
            }
            if (pinnedSource != null && matchesPort(pinnedSource, port)) {
                return reuse(pinnedSource);
            }
            final var candidate = matchingSource(output.getScope(), port);
            if (candidate != null) {
                return reuse(candidate);
            }
            // A fresh intermediate at the output location, itself re-demanded: a multi-hop conversion (e.g.
            // int -> long -> Long). If no strategy ultimately produces it, it acquires no producer and this
            // operation is unreachable by exhaustion (no driver-side guard) — over-emit then prune by cost.
            return new AddValue(output.getScope(), output.getLoc(), port.getType(), port.getNullness());
        }

        private Location childLocation(final String path, final String childName) {
            final var segments = new ArrayList<String>();
            if (!path.isEmpty()) {
                segments.addAll(List.of(path.split("\\.", -1)));
            }
            segments.add(childName);
            return new TargetLocation(new TargetPath(List.copyOf(segments)));
        }

        // ---- source-path descent (leaf-first recursion; the location pins the source) ----------------------

        /** Materializes {@code segments} from the param root, returning the deepest source Value, or {@code null}. */
        private @Nullable Value descend(final Scope scope, final List<String> segments) {
            if (segments.isEmpty()) {
                return null;
            }
            if (segments.size() == SINGLE_SEGMENT) {
                return paramRoot(scope, segments.get(0));
            }
            final var parent = descend(scope, segments.subList(0, segments.size() - 1));
            if (parent == null) {
                return null;
            }
            final var segment = segments.get(segments.size() - 1);
            final var spec = resolveAccessor(type(parent), segment);
            if (spec == null) {
                return null;
            }
            final var loc = new SourceLocation(new AccessPath(List.copyOf(segments)));
            final var key = scope.encode() + "::" + loc.segment();
            if (descended.add(key)) {
                final var portBinding = new PortBinding(
                        spec.getPorts().get(0), new AddValue(scope, parent.getLoc(), type(parent), nullness(parent)));
                apply(new AddOperation(
                        "accessor",
                        spec.getCodegen().getClass().getName(),
                        spec.getCodegen(),
                        spec.getWeight(),
                        spec.isPartial(),
                        List.of(portBinding),
                        new AddValue(scope, loc, spec.getOutputType(), spec.getOutputNullness()),
                        Optional.empty()));
            }
            return graph.valueFor(scope, loc, spec.getOutputType(), spec.getOutputNullness());
        }

        private @Nullable Value paramRoot(final Scope scope, final String segment) {
            return graph.valuesIn(scope)
                    .filter(value -> value.getLoc() instanceof SourceLocation
                            && ((SourceLocation) value.getLoc())
                                    .getPath()
                                    .getSegments()
                                    .equals(List.of(segment)))
                    .findFirst()
                    .orElse(null);
        }

        /** The single one-port accessor a strategy produces for {@code segment} on {@code parentType}, else null. */
        private @Nullable OperationSpec resolveAccessor(final TypeMirror parentType, final String segment) {
            final var demand = new DemandView(
                    parentType,
                    Nullability.NON_NULL,
                    Optional.of(BindingDirective.segment(segment)),
                    Set.of(),
                    segment,
                    List.of(new Candidate(parentType, Nullability.NON_NULL)),
                    resolver);
            return strategies.stream()
                    .flatMap(strategy -> strategy.expand(demand, resolveCtx))
                    .filter(spec -> spec.getPorts().size() == 1
                            && spec.getChildScope().isEmpty()
                            && resolveCtx
                                    .types()
                                    .isSameType(spec.getPorts().get(0).getType(), parentType)
                            && !resolveCtx.types().isSameType(spec.getOutputType(), parentType))
                    .findFirst()
                    .orElse(null);
        }

        // ---- shared ----------------------------------------------------------------------------------------

        private Operation apply(final AddOperation delta) {
            return applier.apply(graph, delta);
        }

        private AddValue outputOf(final Value value) {
            return new AddValue(value.getScope(), value.getLoc(), type(value), nullness(value));
        }

        private AddValue reuse(final Value value) {
            return new AddValue(value.getScope(), value.getLoc(), type(value), nullness(value));
        }

        /** Whether {@code value} can feed {@code port}: same type and a non-null source satisfies any nullness. */
        private boolean matchesPort(final Value value, final Port port) {
            final var nullnessClash =
                    port.getNullness() == Nullability.NON_NULL && nullness(value) == Nullability.NULLABLE;
            return !nullnessClash && resolveCtx.types().isSameType(type(value), port.getType());
        }

        private @Nullable Value matchingSource(final Scope scope, final Port port) {
            return sourceValues(scope)
                    .filter(value -> matchesPort(value, port))
                    .min(Comparator.comparing(Value::id))
                    .orElse(null);
        }

        private Stream<Value> sourceValues(final Scope scope) {
            return graph.valuesIn(scope).filter(value -> {
                final var role = value.getLoc().role();
                return role == Location.Role.SUPPLY || role == Location.Role.ELEMENT;
            });
        }

        private List<Candidate> candidates(final Scope scope) {
            return sourceValues(scope)
                    .map(value -> new Candidate(type(value), nullness(value)))
                    .collect(toUnmodifiableList());
        }

        private List<OperationSpec> run(final DemandView demand) {
            return strategies.stream()
                    .flatMap(strategy -> strategy.expand(demand, resolveCtx))
                    .collect(toUnmodifiableList());
        }

        private List<OperationSpec> dedup(final List<OperationSpec> specs) {
            final var seen = new LinkedHashSet<String>();
            final var unique = new ArrayList<OperationSpec>();
            for (final var spec : specs) {
                if (seen.add(signature(spec))) {
                    unique.add(spec);
                }
            }
            return unique;
        }

        private String signature(final OperationSpec spec) {
            final var ports = spec.getPorts().stream()
                    .map(port -> port.getName() + ':' + port.getType() + ':' + port.getNullness())
                    .collect(Collectors.joining(","));
            return spec.getCodegen().getClass().getName() + '|' + spec.getOutputType() + '|' + ports;
        }

        private TypeMirror type(final Value value) {
            return value.getType().orElseThrow(() -> new IllegalStateException("untyped Value: " + value.id()));
        }

        private Nullability nullness(final Value value) {
            return value.getNullness().orElseThrow(() -> new IllegalStateException("unnulled Value: " + value.id()));
        }

        private List<String> splitPath(final @Nullable String path) {
            if (path == null || path.isEmpty()) {
                return List.of();
            }
            return List.of(path.split("\\.", -1));
        }
    }
}
