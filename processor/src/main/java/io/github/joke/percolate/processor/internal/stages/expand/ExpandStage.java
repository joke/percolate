package io.github.joke.percolate.processor.internal.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.ProcessorOptions;
import io.github.joke.percolate.processor.internal.graph.Location;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.Operation;
import io.github.joke.percolate.processor.internal.graph.PortBinding;
import io.github.joke.percolate.processor.internal.graph.Refusal;
import io.github.joke.percolate.processor.internal.graph.Scope;
import io.github.joke.percolate.processor.internal.graph.TargetLocation;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.processor.internal.stages.Stage;
import io.github.joke.percolate.processor.model.GoalSpec;
import io.github.joke.percolate.processor.model.MapperShape;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.BoundPort;
import io.github.joke.percolate.spi.Constraint;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.SourceProjection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The expansion driver (design D6/D9), a single uniform demand work-list over the bipartite graph: a demanded
 * target {@code Value} asks "what produces this?", the full strategy set answers with {@link OperationSpec}s, and the
 * driver lands each as an atomic {@code AddOperation} whose ports become new demands. There is no per-supply-mode
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
    private final ProcessorOptions options;

    @Override
    public void run(final MapperContext ctx) {
        final var shape = ctx.getShape();
        if (shape == null) {
            return;
        }
        final var graph = new MapperGraph();
        ctx.setGraph(graph);
        final var resolveCtx = new CompileResolveCtx(elements, types, ctx.getCallableMethods(), options.getTimeZone());
        ctx.setResolveCtx(resolveCtx);
        new Driver(strategies, projections, resolver, graph, ctx.getGoalSpecs(), resolveCtx).seedAndExpand(shape);
    }

    /**
     * One expansion run over a single graph (design D5, decomposed by change {@code decompose-engine-stages} into
     * single-method collaborators): an orchestrator composing {@link TargetProducer} (what a FREE demand admits),
     * {@link SourcePathDescender} (a directive's pinned source), {@link PortBinder}/{@link PortSourceResolver} (port
     * sourcing), {@link SelfCallGuard}, and {@link OperationLander} (landing), driven to fixpoint by
     * {@link ExpansionLoop}. Package-visible and {@code static} so the unit suite drives it directly with constructed
     * strategies and an injected {@link ResolveCtx}, asserting on the resulting {@link MapperGraph}. Production code
     * reaches it only through {@link #run(MapperContext)}.
     */
    static final class Driver {

        private final MapperGraph graph;
        private final TargetProducer targetProducer;
        private final SourcePathDescender sourcePathDescender;
        private final PortBinder portBinder;
        private final ResolveCtx resolveCtx;
        private final OperationLander operationLander;
        private final ExpansionLoop expansionLoop;

        Driver(
                final List<ExpansionStrategy> strategies,
                final List<SourceProjection> projections,
                final NullabilityResolver resolver,
                final MapperGraph graph,
                final Map<Scope, GoalSpec> goalSpecs,
                final ResolveCtx resolveCtx) {
            this.graph = graph;
            this.resolveCtx = resolveCtx;
            final var applier = new Applier();
            final var sourceCandidates = new SourceCandidates(graph, applier, resolveCtx);
            final var unifier = new Unifier(resolveCtx);
            final var grounding = new Grounding(
                    new SourceWidener(resolveCtx, projections),
                    new BindingEnumerator(unifier),
                    new SpecInstantiator(resolveCtx));
            this.targetProducer =
                    new TargetProducer(strategies, goalSpecs, sourceCandidates, grounding, resolveCtx, resolver);
            this.operationLander = new OperationLander(graph, applier);
            final var portSourceResolver = new PortSourceResolver(sourceCandidates, operationLander);
            this.portBinder = new PortBinder(portSourceResolver);
            this.sourcePathDescender =
                    new SourcePathDescender(strategies, resolveCtx, resolver, graph, applier, operationLander);
            final var seeder = new Seeder(graph, applier, resolver, goalSpecs);
            this.expansionLoop = new ExpansionLoop(seeder, this::expandValue);
        }

        /**
         * Test-only seam (package-visible): assembles a Driver from already-constructed collaborators, so the unit
         * suite can mock {@link TargetProducer}/{@link SourcePathDescender}/{@link PortBinder}/{@link OperationLander}
         * and exercise {@link #land}/{@link #expandValue} in isolation, per engine-test-quality's orchestrator
         * scenario. {@code resolveCtx} backs only the built-in self-call {@link Constraint}.
         */
        Driver(
                final MapperGraph graph,
                final TargetProducer targetProducer,
                final SourcePathDescender sourcePathDescender,
                final PortBinder portBinder,
                final ResolveCtx resolveCtx,
                final OperationLander operationLander,
                final ExpansionLoop expansionLoop) {
            this.graph = graph;
            this.targetProducer = targetProducer;
            this.sourcePathDescender = sourcePathDescender;
            this.portBinder = portBinder;
            this.resolveCtx = resolveCtx;
            this.operationLander = operationLander;
            this.expansionLoop = expansionLoop;
        }

        /** Self-seeds one return-root demand per abstract method into the empty graph, then drains the work-list. */
        void seedAndExpand(final MapperShape shape) {
            expansionLoop.seedAndExpand(shape);
        }

        /**
         * One step of expansion (the {@link ExpansionLoop.Expander} this driver installs): a FREE target demand asks
         * {@link TargetProducer} what it admits and {@link SourcePathDescender} for its directive-pinned source, then
         * lands each admitted spec, enqueueing every follow-up demand a landed operation's ports and child scope
         * raise. {@code ACCESS} (source-path Values produced by forward descent), {@code LEAF} (parameter/element
         * roots), and {@code CONSTANT} are base cases: nothing to expand.
         */
        void expandValue(final Value value, final Consumer<Value> enqueue) {
            if (value.getLoc().role() != Location.Role.FREE) {
                return;
            }
            final var pinnedSource = sourcePathDescender.pinnedSource(
                    value.getScope(), targetProducer.pinnedSourcePath(value), targetProducer.pinnedDirective(value));
            for (final var spec : targetProducer.produce(value)) {
                land(value, spec, pinnedSource).ifPresent(operation -> {
                    graph.portSourcesOf(operation).forEach(enqueue);
                    operation.getChildScope().ifPresent(child -> enqueue.accept(child.getReturnRoot()));
                });
            }
        }

        /**
         * Turns {@code spec} into a landed {@link Operation} bound by {@code pinnedSource}-ranked sources, or empty
         * when a port can't be sourced ({@link PortBinder}) or an admissibility {@link Constraint} refuses (design
         * D8 of change {@code decouple-engine-from-strategy-semantics}) — a pure function of its inputs, raising no
         * follow-up demand itself (the caller enqueues).
         */
        Optional<Operation> land(final Value output, final OperationSpec spec, final @Nullable Value pinnedSource) {
            final var parentPath = ((TargetLocation) output.getLoc()).getPath().toString();
            return portBinder
                    .bind(output, parentPath, spec, pinnedSource)
                    .filter(ports -> admissible(output, spec, ports))
                    .map(ports -> operationLander.landOperation(spec, ports, operationLander.outputOf(output)));
        }

        /**
         * Applies every constraint bearing on {@code output}'s demand — the engine's own self-call rule plus
         * whatever a reader attached — as a conjunction, recording each refusal on {@code output}'s inadmissible
         * list (design D2). There is exactly one admissibility mechanism: this method, not a second bespoke guard.
         */
        boolean admissible(final Value output, final OperationSpec spec, final List<PortBinding> ports) {
            final var boundPorts = ports.stream()
                    .map(binding -> new BoundPort(binding.getPort(), binding.getSource()))
                    .collect(toUnmodifiableList());
            final var constraints = Stream.concat(
                            Stream.of(new SelfCallConstraint(resolveCtx, output.getScope())),
                            targetProducer.constraintsFor(output).stream())
                    .collect(toUnmodifiableList());
            final var refusals = constraints.stream()
                    .map(constraint -> constraint.check(spec, boundPorts))
                    .flatMap(Optional::stream)
                    .collect(toUnmodifiableList());
            refusals.forEach(
                    refusal -> output.addInadmissible(new Refusal(refusal.getSubject(), refusal.getMessage())));
            return refusals.isEmpty();
        }
    }
}
