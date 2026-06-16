package io.github.joke.percolate.processor.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.AccessPath;
import io.github.joke.percolate.processor.graph.AddOperation;
import io.github.joke.percolate.processor.graph.AddValue;
import io.github.joke.percolate.processor.graph.ChildScopeDecl;
import io.github.joke.percolate.processor.graph.Location;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.graph.Operation;
import io.github.joke.percolate.processor.graph.PortBinding;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.graph.TargetPath;
import io.github.joke.percolate.processor.graph.Value;
import io.github.joke.percolate.processor.model.GoalSpec;
import io.github.joke.percolate.processor.model.MapperShape;
import io.github.joke.percolate.processor.model.MappingDirective;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.processor.stages.Stage;
import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
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
 * left unreachable if nothing produces it. A directive's source path is itself a backward chain of {@code ACCESS}
 * demands ({@link AccessorResolver}): the FREE target's pinned leaf source Value enters the work-list, and each
 * {@code ACCESS} demand resolves its last segment's accessor and re-demands the parent path down to the parameter
 * {@code LEAF} — there is no eager, forward descent.
 *
 * <p>After the work-list drains the graph is fully over-emitted; satisfaction is not computed here — a vertex is
 * reachable iff its extraction cost is finite ({@code ExtractedPlan}), so there is no separate SAT pass.
 */
@RequiredArgsConstructor
public final class ExpandStage implements Stage {

    private final List<ExpansionStrategy> strategies;
    private final Types types;
    private final Elements elements;
    private final NullabilityResolver resolver;

    @Override
    public void run(final MapperContext ctx) {
        final var shape = ctx.getShape();
        if (shape == null) {
            return;
        }
        final var graph = new MapperGraph();
        ctx.setGraph(graph);
        final var resolveCtx = new CompileResolveCtx(elements, types, ctx.getCallableMethods());
        new Driver(graph, ctx.getGoalSpecs(), resolveCtx).seedAndExpand(shape);
    }

    /** One expansion run over a single graph: holds the work-list and the per-Value visited set. */
    private final class Driver {

        private final MapperGraph graph;
        private final Map<Scope, GoalSpec> goalSpecs;
        private final ResolveCtx resolveCtx;
        private final AccessorResolver accessorResolver;
        private final SourceCandidates sourceCandidates;
        private final Applier applier = new Applier();
        private final Deque<Value> workList = new ArrayDeque<>();
        private final Set<Value> visited = new HashSet<>();

        private Driver(final MapperGraph graph, final Map<Scope, GoalSpec> goalSpecs, final ResolveCtx resolveCtx) {
            this.graph = graph;
            this.goalSpecs = goalSpecs;
            this.resolveCtx = resolveCtx;
            this.accessorResolver = new AccessorResolver(strategies, resolveCtx, resolver);
            this.sourceCandidates = new SourceCandidates(graph, applier, resolver, resolveCtx);
        }

        /** Self-seeds one return-type demand per abstract method into the empty graph, then drains the work-list. */
        private void seedAndExpand(final MapperShape shape) {
            shape.getAbstractMethods().forEach(this::seedReturnRoot);
            while (!workList.isEmpty()) {
                final var value = workList.poll();
                if (visited.add(value)) {
                    expand(value);
                }
            }
        }

        /** The only seed: a return-type demand per abstract method, landed through the {@link Applier}. */
        private void seedReturnRoot(final ExecutableElement method) {
            final var scope = new MethodScope(method);
            final var returnType = method.getReturnType();
            final var nullness = resolver.resolve(returnType, method);
            final var root = applier.apply(
                    graph, new AddValue(scope, new TargetLocation(TargetPath.of("")), returnType, nullness));
            enqueue(root);
        }

        private void enqueue(final Value value) {
            workList.add(value);
        }

        private void expand(final Value value) {
            final var role = value.getLoc().role();
            if (role == Location.Role.FREE) {
                expandFree(value);
            } else if (role == Location.Role.ACCESS) {
                expandAccess(value);
            }
            // LEAF (parameter / element roots) and CONSTANT are base cases: nothing to expand.
        }

        /**
         * A FREE target demand: the full strategy set produces it. A directive-pinned source path contributes one
         * typed leaf source {@code Value} as the preferred candidate (the work-list builds its accessor chain).
         */
        private void expandFree(final Value value) {
            final var scope = value.getScope();
            final var path = ((TargetLocation) value.getLoc()).getPath().toString();
            final var goalSpec = goalSpecs.getOrDefault(scope, GoalSpec.from(List.of()));
            final var children = goalSpec.declaredChildren(path);
            final var binding = goalSpec.bindingFor(path);
            final Optional<Directive> directive = binding.map(BindingDirective::from);
            final var pinnedSource = binding.filter(MappingDirective::hasSource)
                    .map(d -> pinnedSource(scope, splitPath(d.getSource())))
                    .orElse(null);
            final var demand = new DemandView(
                    type(value),
                    nullness(value),
                    directive,
                    children,
                    value.getLoc().slotName(),
                    sourceCandidates.candidates(scope),
                    resolver);
            for (final var spec : dedup(run(demand))) {
                land(value, spec, children, pinnedSource);
            }
        }

        /**
         * An ACCESS (multi-segment source-path) demand: only an accessor strategy may produce it. Resolve the last
         * segment's accessor on the parent type, land it through the {@link Applier} producing this Value from the
         * parent {@link SourceLocation}, and enqueue the parent demand so the chain builds target-to-source.
         */
        private void expandAccess(final Value value) {
            final var scope = value.getScope();
            final var segments = ((SourceLocation) value.getLoc()).getPath().getSegments();
            final var parentSegments = segments.subList(0, segments.size() - 1);
            final var parentTyping = accessorResolver.typing(scope, parentSegments);
            if (parentTyping == null) {
                return;
            }
            final var spec =
                    accessorResolver.resolveAccessor(parentTyping.getType(), segments.get(segments.size() - 1));
            if (spec == null) {
                return;
            }
            final var parentSource = new AddValue(
                    scope,
                    new SourceLocation(new AccessPath(List.copyOf(parentSegments))),
                    parentTyping.getType(),
                    parentTyping.getNullness());
            final var operation = apply(new AddOperation(
                    "accessor",
                    spec.getCodegen().getClass().getName(),
                    spec.getCodegen(),
                    spec.getWeight(),
                    spec.isPartial(),
                    List.of(new PortBinding(spec.getPorts().get(0), parentSource)),
                    outputOf(value),
                    Optional.empty()));
            graph.portSourcesOf(operation).forEach(this::enqueue);
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
            if (pinnedSource != null && sourceCandidates.matchesPort(pinnedSource, port)) {
                return reuse(pinnedSource);
            }
            final var candidate = sourceCandidates.matchingSource(output.getScope(), port);
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

        // ---- source-path typing + leaf creation (target-to-source; the location pins the source) -----------

        /**
         * The typed leaf source {@code Value} for {@code segments}, created once via the dedup index so a FREE
         * target's producer can bind it as the preferred candidate; the work-list (ACCESS) then builds its accessor
         * chain. {@code null} when the path is empty or untypable.
         */
        private @Nullable Value pinnedSource(final Scope scope, final List<String> segments) {
            if (segments.isEmpty()) {
                return null;
            }
            final var typing = accessorResolver.typing(scope, segments);
            if (typing == null) {
                return null;
            }
            final var loc = new SourceLocation(new AccessPath(List.copyOf(segments)));
            return applier.apply(graph, new AddValue(scope, loc, typing.getType(), typing.getNullness()));
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
