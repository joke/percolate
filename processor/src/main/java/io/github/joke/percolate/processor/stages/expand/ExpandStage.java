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
import io.github.joke.percolate.spi.SourceProjection;
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
 * left unreachable if nothing produces it. A directive's source path is materialised by <b>forward, target-bound
 * descent</b> when its FREE target is resolved ({@code pinnedSource}): the scope-input root {@code LEAF} is created,
 * then each further segment's accessor (a {@code descend} strategy match) is landed against the type of the Value
 * landed for the previous segment, advancing to the leaf — over-emitting every matching accessor per segment, with
 * no typing pre-walk, no second strategy-invocation site, and no backward parent re-demand.
 *
 * <p>After the work-list drains the graph is fully over-emitted; satisfaction is not computed here — a vertex is
 * reachable iff its extraction cost is finite ({@code ExtractedPlan}), so there is no separate SAT pass.
 */
@RequiredArgsConstructor
public final class ExpandStage implements Stage {

    private final List<ExpansionStrategy> strategies;
    private final List<SourceProjection> projections;
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
        private final SourceCandidates sourceCandidates;
        private final Grounding grounding;
        private final Applier applier = new Applier();
        private final SelfCallGuard selfCallGuard = new SelfCallGuard();
        private final Deque<Value> workList = new ArrayDeque<>();
        private final Set<Value> visited = new HashSet<>();

        private Driver(final MapperGraph graph, final Map<Scope, GoalSpec> goalSpecs, final ResolveCtx resolveCtx) {
            this.graph = graph;
            this.goalSpecs = goalSpecs;
            this.resolveCtx = resolveCtx;
            this.sourceCandidates = new SourceCandidates(graph, applier, resolver, resolveCtx);
            this.grounding = new Grounding(resolveCtx, projections);
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

        /** The only seed: a return-type demand per abstract method, landed through the {@link Applier} and recorded
         * as the method's return root (the authority a method may not satisfy by self-call, and the single root
         * extraction/diagnostics/codegen key on — not the same-location intermediates over-emission later mints). */
        private void seedReturnRoot(final ExecutableElement method) {
            final var scope = new MethodScope(method);
            final var returnType = method.getReturnType();
            final var nullness = resolver.resolve(returnType, method);
            final var root = applier.apply(
                    graph, new AddValue(scope, new TargetLocation(TargetPath.of("")), returnType, nullness));
            graph.markReturnRoot(root);
            enqueue(root);
        }

        private void enqueue(final Value value) {
            workList.add(value);
        }

        private void expand(final Value value) {
            if (value.getLoc().role() == Location.Role.FREE) {
                expandFree(value);
            }
            // LEAF (parameter / element roots), ACCESS (source-path Values produced by forward descent), and
            // CONSTANT are base cases: nothing to expand. A multi-segment source Value is produced forward by
            // pinnedSource's descent, never demanded and walked backward.
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
                    resolver);
            // Ground type-variable ports against the in-scope sources before dedup/land: every spec the driver
            // lands is concrete (no abstract type ever enters the work-list), preserving target→source order and
            // over-emit + cost-prune. A concrete spec passes through grounding unchanged.
            final var sourceTypes = sourceCandidates.sourceTypes(scope);
            final var grounded = run(demand, resolveCtx).stream()
                    .flatMap(spec -> grounding.ground(spec, sourceTypes))
                    .collect(toUnmodifiableList());
            for (final var spec : dedup(grounded)) {
                land(value, spec, pinnedSource);
            }
        }

        // ---- landing an operation -------------------------------------------------------------------------

        private void land(final Value output, final OperationSpec spec, final @Nullable Value pinnedSource) {
            final var parentPath = ((TargetLocation) output.getLoc()).getPath().toString();
            final var ports = new ArrayList<PortBinding>();
            for (final var port : spec.getPorts()) {
                final var source = sourceForPort(output, parentPath, port, pinnedSource);
                if (source == null) {
                    return; // a REUSE port found no in-scope source: this producer does not apply
                }
                ports.add(new PortBinding(port, source));
            }
            if (selfCallGuard.refuses(output.getScope(), spec, ports)) {
                return; // a method may not call itself on its own whole parameter (degenerate infinite recursion)
            }
            final var operation = landOperation(spec, ports, outputOf(output));
            graph.portSourcesOf(operation).forEach(this::enqueue);
            operation.getChildScope().ifPresent(child -> enqueue(child.getReturnRoot()));
        }

        /**
         * The single {@link AddOperation}-construction primitive behind both walks: the producer path ({@link #land})
         * and the accessor-descent path ({@link #descendSegment}) differ only in how they pin the output and resolve
         * the ports; building and applying the delta (with any child-scope declaration) is shared here.
         */
        private Operation landOperation(
                final OperationSpec spec, final List<PortBinding> ports, final AddValue output) {
            return apply(new AddOperation(
                    spec.getLabel(),
                    spec.getCodegen(),
                    spec.getWeight(),
                    spec.isPartial(),
                    ports,
                    output,
                    spec.getChildScope()
                            .map(child -> new ChildScopeDecl(
                                    child.getElementIn(),
                                    child.getElementInNullness(),
                                    child.getElementOut(),
                                    child.getElementOutNullness()))));
        }

        /**
         * Binds one port to a feeding {@code Value} (D1, "a port is a demand") by dispatching on its declared
         * {@link Port.Sourcing} mode — never reconstructing intent from a name-match or a boolean. {@code SUBTARGET}
         * mints a deeper child-target demand at the child location; {@code REUSE} and {@code REUSE_OR_MINT} both bind
         * an in-scope source (directive-{@code pinnedSource} ranked first by {@link SourceCandidates}), differing only
         * when none is found — {@code REUSE} declines (the operation does not apply, never minted) while
         * {@code REUSE_OR_MINT} mints a fresh intermediate at the output location, itself re-demanded (a multi-hop
         * conversion) and pruned by cost if nothing produces it. The demand's declared-children set no longer
         * participates here — it gates assembly in the demand only.
         */
        private @Nullable AddValue sourceForPort(
                final Value output, final String parentPath, final Port port, final @Nullable Value pinnedSource) {
            if (port.getSourcing() == Port.Sourcing.SUBTARGET) {
                return new AddValue(
                        output.getScope(),
                        childLocation(parentPath, port.getName()),
                        port.getType(),
                        port.getNullness());
            }
            final var reused = sourceCandidates.matchingSource(output.getScope(), port, pinnedSource);
            if (reused != null) {
                return reuse(reused);
            }
            // A REUSE port whose input is larger than its output is never minted (you never wrap a value just to
            // unwrap it): with no in-scope source the consuming operation simply does not apply.
            return port.getSourcing() == Port.Sourcing.REUSE
                    ? null
                    : new AddValue(output.getScope(), output.getLoc(), port.getType(), port.getNullness());
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
         * The leaf source {@code Value} for a directive's source {@code segments}, materialised by forward,
         * target-bound descent (design D1/D2): the scope-input root {@code LEAF} is created, then each further
         * segment's accessor is landed against the type of the {@code Value} landed for the previous segment,
         * advancing to the leaf — over-emitting every matching accessor per segment (cost prunes later), with no
         * typing pre-walk and no {@code findFirst}. It runs here, before the demand's ports bind, so the leaf is the
         * preferred source for a directive-bound target. {@code null} when the path is empty or a segment resolves no
         * accessor. Idempotent through the dedup index, so re-deriving the same source path re-lands nothing new.
         */
        private @Nullable Value pinnedSource(final Scope scope, final List<String> segments) {
            if (segments.isEmpty()) {
                return null;
            }
            var parent = materialiseRoot(scope, segments.get(0));
            for (var depth = 1; parent != null && depth < segments.size(); depth++) {
                parent = descendSegment(scope, parent, segments.subList(0, depth + 1));
            }
            return parent;
        }

        /** The scope-input root {@code LEAF} for the path's first {@code segment}, typed from the scope's input
         * declaration uniformly across method and child scopes (no scope-kind branch); {@code null} when no input
         * declares it. */
        private @Nullable Value materialiseRoot(final Scope scope, final String segment) {
            return scope.inputDecls(resolver::resolve)
                    .filter(decl -> decl.getLocation().slotName().equals(segment))
                    .findFirst()
                    .map(decl -> applier.apply(
                            graph, new AddValue(scope, decl.getLocation(), decl.getType(), decl.getNullness())))
                    .orElse(null);
        }

        /**
         * Lands every accessor that reads {@code path}'s last segment off {@code parent}, returning the produced
         * source {@code Value} at {@code path} — the deduped child shared by equal-typed accessors (getter and field
         * over-emit two Operations into one Value; cost prunes). {@code null} when no accessor resolves the segment.
         */
        private @Nullable Value descendSegment(final Scope scope, final Value parent, final List<String> path) {
            final var demand = new DescendView(type(parent), nullness(parent), path.get(path.size() - 1), resolver);
            final var childLoc = new SourceLocation(new AccessPath(List.copyOf(path)));
            Value child = null;
            for (final var spec : dedup(descend(demand, resolveCtx))) {
                final var ports = List.of(new PortBinding(spec.getPorts().get(0), reuse(parent)));
                final var output = new AddValue(scope, childLoc, spec.getOutputType(), spec.getOutputNullness());
                final var operation = landOperation(spec, ports, output);
                if (child == null) {
                    child = graph.outputOf(operation).orElse(null);
                }
            }
            return child;
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

        private List<OperationSpec> run(final DemandView demand, final ResolveCtx ctx) {
            return strategies.stream()
                    .flatMap(strategy -> strategy.expand(demand, ctx))
                    .collect(toUnmodifiableList());
        }

        private List<OperationSpec> descend(final DescendView demand, final ResolveCtx ctx) {
            return strategies.stream()
                    .flatMap(strategy -> strategy.descend(demand, ctx))
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
            return spec.getLabel() + '|' + spec.getOutputType() + '|' + ports;
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
