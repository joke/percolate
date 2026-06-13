package io.github.joke.percolate.processor.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.MapperContext;
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
import io.github.joke.percolate.spi.AssemblyStrategy;
import io.github.joke.percolate.spi.Candidate;
import io.github.joke.percolate.spi.Codegen;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The expansion driver (design D6/D9). It runs a target-to-source demand work-list over the bipartite graph: a
 * demanded target {@code Value} asks "what produces this?", strategies answer with {@link OperationSpec}s, and the
 * driver lands each as an atomic {@link AddOperation} whose ports become new demands. There is no group machinery
 * and no forward sweep.
 *
 * <p>Three supply modes follow from the goal spec and the binding directive carried by the demand:
 * <ul>
 *   <li><b>assembly</b> — a target level with declared children is built by a constructor (gated on exact
 *       parameter-name match); each parameter port becomes a child target demand;</li>
 *   <li><b>bridge</b> — a leaf target is produced from its directive's source: the source path is materialized
 *       into per-segment accessor Operations, then bridge/conversion/constant/default strategies produce the
 *       target from the deepest source value (with nullness crossings inserted at port binding);</li>
 *   <li><b>container element mapping</b> — a scope-owning Operation's child return-root joins the work-list with
 *       the element param-root as its candidate.</li>
 * </ul>
 *
 * After the work-list drains, {@link HornSat} recomputes the SAT predicate over the whole graph.
 */
@RequiredArgsConstructor
public final class ExpandStage implements Stage {

    private final List<ExpansionStrategy> assemblyStrategies;
    private final List<ExpansionStrategy> generalStrategies;
    private final ResolveCtx resolveCtx;
    private final NullabilityResolver resolver;

    public ExpandStage(
            final List<ExpansionStrategy> strategies, final ResolveCtx resolveCtx, final NullabilityResolver resolver) {
        this.assemblyStrategies =
                strategies.stream().filter(AssemblyStrategy.class::isInstance).collect(toUnmodifiableList());
        this.generalStrategies = strategies.stream()
                .filter(strategy -> !(strategy instanceof AssemblyStrategy))
                .collect(toUnmodifiableList());
        this.resolveCtx = resolveCtx;
        this.resolver = resolver;
    }

    @Override
    public void run(final MapperContext ctx) {
        final var graph = ctx.getGraph();
        if (graph == null) {
            return;
        }
        final var goalSpecs = ctx.getGoalSpecs();
        new Driver(graph, goalSpecs).expandAll();
        HornSat.propagate(graph);
    }

    /** One expansion run over a single graph: holds the work-list and the per-Value visited set. */
    @SuppressWarnings("PMD.GodClass") // cohesive expansion engine; FrontierMatcher/Applier split is a tracked follow-up
    private final class Driver {

        private final MapperGraph graph;
        private final Map<Scope, GoalSpec> goalSpecs;
        private final Deque<DemandItem> workList = new ArrayDeque<>();
        private final Set<Value> visited = new HashSet<>();

        private Driver(final MapperGraph graph, final Map<Scope, GoalSpec> goalSpecs) {
            this.graph = graph;
            this.goalSpecs = goalSpecs;
        }

        private void expandAll() {
            graph.values()
                    .filter(value -> value.getLoc().isReturnRoot())
                    .collect(toUnmodifiableList())
                    .forEach(root -> enqueue(root, List.of()));
            while (!workList.isEmpty()) {
                final var item = workList.poll();
                if (visited.add(item.value)) {
                    expand(item);
                }
            }
        }

        private void enqueue(final Value value, final List<Value> candidates) {
            workList.add(new DemandItem(value, candidates));
        }

        private void expand(final DemandItem item) {
            final var value = item.value;
            if (!(value.getLoc() instanceof TargetLocation)) {
                return;
            }
            final var path = ((TargetLocation) value.getLoc()).getPath().toString();
            final var goalSpec = goalSpecs.getOrDefault(value.getScope(), GoalSpec.from(List.of()));
            final var children = goalSpec.declaredChildren(path);
            if (children.isEmpty()) {
                expandBridge(item, goalSpec.bindingFor(path));
            } else {
                expandAssembly(value, path, children);
            }
        }

        // ---- assembly --------------------------------------------------------------------------------------

        private void expandAssembly(final Value value, final String path, final Set<String> children) {
            final var demand =
                    new DemandView(type(value), nullness(value), Optional.empty(), children, List.of(), resolver);
            for (final var spec : dedup(run(assemblyStrategies, demand))) {
                final var ports = spec.getPorts().stream()
                        .map(port -> new PortBinding(port, childTarget(value, path, port)))
                        .collect(toUnmodifiableList());
                land(spec, outputOf(value), ports);
                spec.getPorts().forEach(port -> enqueue(resolvePort(value.getScope(), path, port), List.of()));
            }
        }

        private AddValue childTarget(final Value parent, final String path, final Port port) {
            return new AddValue(
                    parent.getScope(), childLocation(path, port.getName()), port.getType(), port.getNullness());
        }

        private Value resolvePort(final Scope scope, final String path, final Port port) {
            return graph.valueFor(scope, childLocation(path, port.getName()), port.getType(), port.getNullness());
        }

        private Location childLocation(final String path, final String childName) {
            final var segments = new ArrayList<String>();
            if (!path.isEmpty()) {
                segments.addAll(List.of(path.split("\\.", -1)));
            }
            segments.add(childName);
            return new TargetLocation(new TargetPath(List.copyOf(segments)));
        }

        // ---- bridge ----------------------------------------------------------------------------------------

        private void expandBridge(final DemandItem item, final Optional<MappingDirective> binding) {
            final var value = item.value;
            final var candidates = new ArrayList<>(item.candidates);
            final Optional<Directive> directive = binding.map(BindingDirective::from);
            binding.filter(MappingDirective::hasSource).ifPresent(d -> {
                final var deepest = materializeSource(value.getScope(), splitPath(d.getSource()));
                if (deepest != null) {
                    candidates.add(deepest);
                }
            });
            final var demand = new DemandView(
                    type(value), nullness(value), directive, Set.of(), candidatesOf(candidates), resolver);
            for (final var spec : dedup(run(generalStrategies, demand))) {
                applyBridge(value, spec, candidates);
            }
        }

        private void applyBridge(final Value value, final OperationSpec spec, final List<Value> candidates) {
            // No silent sourcing: with no source candidate, only a zero-port producer (e.g. a constant) can land.
            // A conversion's port would otherwise synthesize a spurious source-less intermediate of its input type.
            if (!spec.getPorts().isEmpty() && candidates.isEmpty()) {
                return;
            }
            final var ports = spec.getPorts().stream()
                    .map(port -> new PortBinding(port, bindPort(value, port, candidates)))
                    .collect(toUnmodifiableList());
            final var operation = land(spec, outputOf(value), ports);
            spec.getChildScope().ifPresent(decl -> operation
                    .getChildScope()
                    .ifPresent(child -> enqueue(child.getReturnRoot(), List.of(child.getParamRoot()))));
            // A synthesized conversion intermediate (no matching candidate) becomes a new bridge demand.
            for (final var port : spec.getPorts()) {
                if (matching(candidates, port.getType()).isEmpty()) {
                    enqueue(
                            graph.valueFor(value.getScope(), value.getLoc(), port.getType(), port.getNullness()),
                            candidates);
                }
            }
        }

        /**
         * Binds an operation's port to a feeding Value: a candidate source value of the port's type when one
         * exists (inserting a {@code requireNonNull} nullness crossing when the source is nullable but the port
         * is non-null), otherwise a fresh conversion intermediate at the target location for a further bridge.
         */
        private AddValue bindPort(final Value target, final Port port, final List<Value> candidates) {
            final var match = matching(candidates, port.getType());
            if (match.isEmpty()) {
                return new AddValue(target.getScope(), target.getLoc(), port.getType(), port.getNullness());
            }
            final var source = match.get();
            if (port.getNullness() == Nullability.NON_NULL && nullness(source) == Nullability.NULLABLE) {
                crossNonNull(source, bindingName(target, port));
                return new AddValue(source.getScope(), source.getLoc(), type(source), Nullability.NON_NULL);
            }
            return new AddValue(source.getScope(), source.getLoc(), type(source), nullness(source));
        }

        /** The user-facing binding name for a crossing message: the target field's name, else the port name. */
        private String bindingName(final Value target, final Port port) {
            final var slot = target.getLoc().slotName();
            return slot.isEmpty() ? port.getName() : slot;
        }

        /** Emits a {@code [requireNonNull]} unary Operation producing a NON_NULL value from a nullable source. */
        private void crossNonNull(final Value source, final String slotName) {
            final var message = "source for slot '" + slotName + "' is null but target is non-null";
            final Codegen codegen = (io.github.joke.percolate.spi.OperationCodegen)
                    (vars, inputs) -> com.palantir.javapoet.CodeBlock.of(
                            "$T.requireNonNull($L, $S)", Objects.class, inputs.single(), message);
            final var output = new AddValue(source.getScope(), source.getLoc(), type(source), Nullability.NON_NULL);
            final var input = new PortBinding(
                    new Port("value", type(source), Nullability.NULLABLE),
                    new AddValue(source.getScope(), source.getLoc(), type(source), Nullability.NULLABLE));
            apply(new AddOperation(
                    "requireNonNull", "engine:requireNonNull", codegen, 0, List.of(input), output, Optional.empty()));
        }

        // ---- source descent --------------------------------------------------------------------------------

        /** Materializes the source access path into per-segment accessor Operations; returns the deepest Value. */
        @Nullable
        private Value materializeSource(final Scope scope, final List<String> segments) {
            if (segments.isEmpty()) {
                return null;
            }
            var prev = findValue(
                    scope,
                    loc -> loc instanceof SourceLocation
                            && ((SourceLocation) loc).getPath().getSegments().equals(segments.subList(0, 1)));
            if (prev == null) {
                return null;
            }
            for (var depth = 1; depth < segments.size(); depth++) {
                final var spec = resolveAccessor(type(prev), segments.get(depth));
                if (spec == null) {
                    return null;
                }
                final var loc = new SourceLocation(new io.github.joke.percolate.processor.graph.AccessPath(
                        List.copyOf(segments.subList(0, depth + 1))));
                final var output = new AddValue(scope, loc, spec.getOutputType(), spec.getOutputNullness());
                final var port = new PortBinding(
                        spec.getPorts().get(0), new AddValue(scope, prev.getLoc(), type(prev), nullness(prev)));
                apply(new AddOperation(
                        "accessor",
                        spec.getCodegen().getClass().getName(),
                        spec.getCodegen(),
                        spec.getWeight(),
                        List.of(port),
                        output,
                        Optional.empty()));
                prev = graph.valueFor(scope, loc, spec.getOutputType(), spec.getOutputNullness());
            }
            return prev;
        }

        @Nullable
        private OperationSpec resolveAccessor(final TypeMirror parentType, final String segment) {
            final var demand = new DemandView(
                    parentType,
                    Nullability.NON_NULL,
                    Optional.of(BindingDirective.segment(segment)),
                    Set.of(),
                    List.of(new Candidate(parentType)),
                    resolver);
            return generalStrategies.stream()
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

        // ---- shared --------------------------------------------------------------------------------------

        private Operation land(final OperationSpec spec, final AddValue output, final List<PortBinding> ports) {
            return apply(new AddOperation(
                    spec.getCodegen().getClass().getSimpleName(),
                    spec.getCodegen().getClass().getName(),
                    spec.getCodegen(),
                    spec.getWeight(),
                    ports,
                    output,
                    spec.getChildScope()
                            .map(child -> new ChildScopeDecl(
                                    child.getElementIn(),
                                    child.getElementInNullness(),
                                    child.getElementOut(),
                                    child.getElementOutNullness()))));
        }

        private Operation apply(final AddOperation delta) {
            return graph.apply(delta);
        }

        private AddValue outputOf(final Value value) {
            return new AddValue(value.getScope(), value.getLoc(), type(value), nullness(value));
        }

        private List<OperationSpec> run(final List<ExpansionStrategy> strategies, final DemandView demand) {
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

        private Optional<Value> matching(final List<Value> candidates, final TypeMirror portType) {
            return candidates.stream()
                    .filter(candidate -> resolveCtx.types().isSameType(type(candidate), portType))
                    .min(Comparator.comparing(Value::id));
        }

        private List<Candidate> candidatesOf(final List<Value> candidates) {
            return candidates.stream().map(value -> new Candidate(type(value))).collect(toUnmodifiableList());
        }

        @Nullable
        private Value findValue(final Scope scope, final java.util.function.Predicate<Location> locPredicate) {
            return graph.valuesIn(scope)
                    .filter(value -> locPredicate.test(value.getLoc()))
                    .findFirst()
                    .orElse(null);
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

    /** A target {@code Value} to satisfy, plus the source candidate Values available to bridge it. */
    @RequiredArgsConstructor
    private static final class DemandItem {
        private final Value value;
        private final List<Value> candidates;
    }
}
