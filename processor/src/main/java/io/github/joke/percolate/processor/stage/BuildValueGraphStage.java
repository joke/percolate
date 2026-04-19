package io.github.joke.percolate.processor.stage;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.graph.LiftEdge;
import io.github.joke.percolate.processor.graph.LiftKind;
import io.github.joke.percolate.processor.graph.PropertyNode;
import io.github.joke.percolate.processor.graph.PropertyReadEdge;
import io.github.joke.percolate.processor.graph.SourceParamNode;
import io.github.joke.percolate.processor.graph.TargetSlotNode;
import io.github.joke.percolate.processor.graph.TypeTransformEdge;
import io.github.joke.percolate.processor.graph.TypedValueNode;
import io.github.joke.percolate.processor.graph.ValueEdge;
import io.github.joke.percolate.processor.graph.ValueGraphResult;
import io.github.joke.percolate.processor.graph.ValueNode;
import io.github.joke.percolate.processor.match.MappingAssignment;
import io.github.joke.percolate.processor.match.MatchedModel;
import io.github.joke.percolate.processor.match.MethodMatching;
import io.github.joke.percolate.processor.match.ResolutionFailure;
import io.github.joke.percolate.processor.model.ReadAccessor;
import io.github.joke.percolate.processor.model.WriteAccessor;
import io.github.joke.percolate.processor.spi.ResolutionContext;
import io.github.joke.percolate.processor.spi.SourcePropertyDiscovery;
import io.github.joke.percolate.processor.spi.TargetPropertyDiscovery;
import io.github.joke.percolate.processor.spi.TypeTransformStrategy;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;
import org.jgrapht.alg.shortestpath.BFSShortestPath;
import org.jgrapht.graph.DefaultDirectedGraph;

/**
 * Builds one typed {@code DefaultDirectedGraph<ValueNode, ValueEdge>} per method in the
 * {@link MatchedModel}.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Create one {@link SourceParamNode} per method parameter.</li>
 *   <li>Walk each {@link MappingAssignment}'s source path, reusing or creating
 *       {@link PropertyNode}s via {@link SourcePropertyDiscovery}.</li>
 *   <li>Create one {@link TargetSlotNode} per assignment via {@link TargetPropertyDiscovery}.</li>
 *   <li>Run the 30-iteration strategy fixpoint loop to propose {@link TypeTransformEdge}s.</li>
 *   <li>Assert graph invariants (target-slot leaves, edge type constraints).</li>
 * </ol>
 *
 * <p>The graph may contain directed cycles. Inverse strategy pairs (e.g. {@code OptionalWrap}
 * /{@code OptionalUnwrap}, {@code TemporalToString}/{@code StringToTemporal}) coexist as
 * 2-cycles between the same two typed nodes. {@link BFSShortestPath} traverses cyclic graphs
 * correctly via a visited set, and downstream stages walk only resolved {@code GraphPath}s.
 *
 * <p>BFS path search is NOT performed here — that is {@code ResolvePathStage}'s job.
 */
@RequiredArgsConstructor
public final class BuildValueGraphStage {

    private static final int MAX_ITERATIONS = 30;

    private final Types types;
    private final Elements elements;
    private final List<TypeTransformStrategy> strategies;
    private final List<SourcePropertyDiscovery> sourceDiscoveries;
    private final List<TargetPropertyDiscovery> targetDiscoveries;

    @Inject
    BuildValueGraphStage(final Types types, final Elements elements) {
        this(
                types,
                elements,
                loadServices(TypeTransformStrategy.class),
                loadAndSortByPriority(SourcePropertyDiscovery.class, SourcePropertyDiscovery::priority),
                loadAndSortByPriority(TargetPropertyDiscovery.class, TargetPropertyDiscovery::priority));
    }

    public StageResult<ValueGraphResult> execute(final MatchedModel matchedModel) {
        final Map<MethodMatching, DefaultDirectedGraph<ValueNode, ValueEdge>> graphs = new LinkedHashMap<>();
        final Map<MappingAssignment, ResolutionFailure> failures = new LinkedHashMap<>();

        for (final MethodMatching matching : matchedModel.getMethods()) {
            final var graph = buildMethodGraph(matching, matchedModel.getMapperType(), failures);
            assertInvariants(graph);
            graphs.put(matching, graph);
        }

        return StageResult.success(new ValueGraphResult(Map.copyOf(graphs), Map.copyOf(failures)));
    }

    private DefaultDirectedGraph<ValueNode, ValueEdge> buildMethodGraph(
            final MethodMatching matching,
            final TypeElement mapperType,
            final Map<MappingAssignment, ResolutionFailure> failures) {

        final var graph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge.class);

        // 6.2 — one SourceParamNode per method parameter
        final Map<String, SourceParamNode> paramNodes = new LinkedHashMap<>();
        for (final var param : matching.getMethod().getParameters()) {
            final var node = new SourceParamNode((VariableElement) param, param.asType());
            graph.addVertex(node);
            paramNodes.put(node.getName(), node);
        }

        // Shared type index for dedup of TypedValueNode / TargetSlotNode within this method
        final Map<String, ValueNode> typeIndex = new LinkedHashMap<>();

        // Seed with param types (so strategies can propose from them)
        for (final var param : paramNodes.values()) {
            typeIndex.putIfAbsent(param.getType().toString(), param);
        }

        // 6.3 + 6.4 + 6.5 — for each assignment, walk source path, create target slot, propose edges
        final boolean multiParam = matching.getMethod().getParameters().size() > 1;
        for (final MappingAssignment assignment : matching.getAssignments()) {
            buildAssignmentEdges(graph, paramNodes, multiParam, matching, mapperType, assignment, typeIndex, failures);
        }

        return graph;
    }

    @SuppressWarnings("NullAway") // startParam null case returns early; currentType is always non-null in the loop
    private void buildAssignmentEdges(
            final DefaultDirectedGraph<ValueNode, ValueEdge> graph,
            final Map<String, SourceParamNode> paramNodes,
            final boolean multiParam,
            final MethodMatching matching,
            final TypeElement mapperType,
            final MappingAssignment assignment,
            final Map<String, ValueNode> typeIndex,
            final Map<MappingAssignment, ResolutionFailure> failures) {

        final List<String> sourcePath = assignment.getSourcePath();

        // Select the starting SourceParamNode and first property-segment index
        final SourceParamNode startParam;
        final int pathStart;
        if (multiParam) {
            startParam = paramNodes.get(sourcePath.get(0));
            if (startParam == null) {
                return; // ValidateMatchingStage already caught this
            }
            pathStart = 1;
        } else {
            startParam = paramNodes.values().iterator().next();
            pathStart = 0;
        }

        // 6.3 — walk the property access chain
        ValueNode parent = startParam;
        TypeMirror currentType = startParam.getType();

        for (int i = pathStart; i < sourcePath.size(); i++) {
            final String segmentName = sourcePath.get(i);
            final var availableProps = discoverSourcePropertyMap(currentType);
            final var accessor = availableProps.get(segmentName);

            if (accessor == null) {
                failures.put(assignment, new ResolutionFailure(segmentName, Set.copyOf(availableProps.keySet())));
                return; // cannot continue this path
            }

            final var propNode = findOrCreatePropertyNode(graph, parent, segmentName, accessor);
            parent = propNode;
            currentType = accessor.getType();
        }

        // Register leaf in type index so the fixpoint can propose from it
        typeIndex.putIfAbsent(currentType.toString(), parent);

        // 6.4 — create TargetSlotNode
        final var targetProps = discoverTargetPropertyMap(matching.getModel().getTargetType());
        final var targetAccessor = targetProps.get(assignment.getTargetName());
        if (targetAccessor == null) {
            return; // ValidateResolutionStage will diagnose this
        }

        final var targetSlot = new TargetSlotNode(assignment.getTargetName(), targetAccessor.getType(), targetAccessor);
        if (!graph.containsVertex(targetSlot)) {
            graph.addVertex(targetSlot);
        }
        typeIndex.putIfAbsent(targetSlot.getType().toString(), targetSlot);

        // 6.5 — fixpoint loop: propose TypeTransformEdges for this assignment
        final var ctx = new ResolutionContext(
                types,
                elements,
                mapperType,
                matching.getMethod(),
                assignment.getOptions(),
                assignment.getUsing() != null ? assignment.getUsing() : "");

        proposeTransformEdges(graph, typeIndex, ctx, parent, targetSlot);
    }

    /**
     * Runs the 30-iteration fixpoint loop, asking each strategy to propose
     * {@link TypeTransformEdge}s (and {@link LiftEdge}s for lift strategies) for the given
     * assignment's source leaf → target slot pair.
     *
     * <p>The {@code from} side is restricted to {@code sourceLeaf} and any {@link TypedValueNode}
     * in the graph, preventing cross-connections from unrelated {@link PropertyNode}s. The
     * {@code to} side is restricted to {@code targetSlot} and any {@link TypedValueNode}.
     *
     * <p>When a strategy's required-input type matches {@code from.getType()}, {@code from} is
     * used directly as the edge source, avoiding self-loops and ensuring the shortest BFS path.
     *
     * <p>BFS for the main assignment path is NOT called here — that belongs to
     * {@code ResolvePathStage}. BFS for {@code LiftEdge} inner paths IS run post-fixpoint.
     */
    @SuppressWarnings("NullAway") // prop.getCodeTemplate() is @Nullable but TypeTransformEdge accepts it
    private void proposeTransformEdges(
            final DefaultDirectedGraph<ValueNode, ValueEdge> graph,
            final Map<String, ValueNode> typeIndex,
            final ResolutionContext ctx,
            final ValueNode sourceLeaf,
            final TargetSlotNode targetSlot) {

        final List<PendingLift> pendingLifts = new ArrayList<>();

        for (var iter = 0; iter < MAX_ITERATIONS; iter++) {
            var expanded = false;
            final var currentVertices = sortBySourceTargetReachability(graph, sourceLeaf, targetSlot);

            for (final var from : currentVertices) {
                // Restrict "from" to the source leaf and TypedValueNodes only
                if (from != sourceLeaf && !(from instanceof TypedValueNode)) {
                    continue;
                }
                for (final var to : currentVertices) {
                    // Restrict "to" to the target slot and TypedValueNodes only
                    if (to != targetSlot && !(to instanceof TypedValueNode)) {
                        continue;
                    }
                    if (from == to) {
                        continue;
                    }

                    for (final var strategy : strategies) {
                        final var proposal = strategy.canProduce(from.getType(), to.getType(), ctx);
                        if (proposal.isEmpty()) {
                            continue;
                        }
                        final var prop = proposal.get();

                        // Use from/to directly when types match to avoid self-loops and ensure
                        // direct (shorter) edges that BFS prefers over cross-connections.
                        final ValueNode inputNode = prop.getRequiredInput()
                                        .toString()
                                        .equals(from.getType().toString())
                                ? from
                                : getOrCreateTypedNode(graph, typeIndex, prop.getRequiredInput());
                        final ValueNode outputNode = prop.getProducedOutput()
                                        .toString()
                                        .equals(to.getType().toString())
                                ? to
                                : getOrCreateTypedNode(graph, typeIndex, prop.getProducedOutput());

                        if (inputNode == outputNode) {
                            continue; // safety: skip self-loops
                        }

                        if (prop.getLiftKind() != null
                                && prop.getLiftInnerInput() != null
                                && prop.getLiftInnerOutput() != null) {
                            // Lift proposal: ensure inner type nodes exist so subsequent fixpoint
                            // iterations can populate inner edges.
                            final var innerIn = getOrCreateTypedNode(graph, typeIndex, prop.getLiftInnerInput());
                            final var innerOut = getOrCreateTypedNode(graph, typeIndex, prop.getLiftInnerOutput());
                            final boolean alreadyPending = pendingLifts.stream()
                                    .anyMatch(p -> (p.inputNode.equals(inputNode) && p.outputNode.equals(outputNode))
                                            || (p.inputNode.equals(outputNode) && p.outputNode.equals(inputNode)));
                            if (!alreadyPending && !graph.containsEdge(inputNode, outputNode)) {
                                pendingLifts.add(
                                        new PendingLift(inputNode, outputNode, prop.getLiftKind(), innerIn, innerOut));
                                expanded = true;
                            }
                        } else if (!graph.containsEdge(inputNode, outputNode)) {
                            graph.addEdge(
                                    inputNode,
                                    outputNode,
                                    new TypeTransformEdge(
                                            strategy,
                                            prop.getRequiredInput(),
                                            prop.getProducedOutput(),
                                            prop.getCodeTemplate()));
                            expanded = true;
                        }
                    }
                }
            }

            if (!expanded) {
                break;
            }
        }

        // Post-fixpoint: create LiftEdges carrying (kind, innerInputNode, innerOutputNode).
        // The inner type nodes were added to the graph during the fixpoint, so inner edges
        // are already present. The inner path is resolved lazily by LiftEdge.composeTemplate(...)
        // at generation time — but we guard here by probing the inner BFS, so a lift without
        // a reachable inner path is dropped rather than producing a dangling outer edge that
        // would later crash GenerateStage.
        for (final var lift : pendingLifts) {
            if (graph.containsEdge(lift.inputNode, lift.outputNode)) {
                continue;
            }
            final var innerProbe = new org.jgrapht.alg.shortestpath.BFSShortestPath<>(graph)
                    .getPath(lift.innerInputNode, lift.innerOutputNode);
            if (innerProbe == null) {
                continue;
            }
            graph.addEdge(
                    lift.inputNode,
                    lift.outputNode,
                    new LiftEdge(lift.kind, lift.innerInputNode, lift.innerOutputNode));
        }
    }

    private static final class PendingLift {
        final ValueNode inputNode;
        final ValueNode outputNode;
        final LiftKind kind;
        final ValueNode innerInputNode;
        final ValueNode innerOutputNode;

        PendingLift(
                final ValueNode inputNode,
                final ValueNode outputNode,
                final LiftKind kind,
                final ValueNode innerInputNode,
                final ValueNode innerOutputNode) {
            this.inputNode = inputNode;
            this.outputNode = outputNode;
            this.kind = kind;
            this.innerInputNode = innerInputNode;
            this.innerOutputNode = innerOutputNode;
        }
    }

    /**
     * Finds an existing {@link PropertyNode} equal to {@code (name, type)} in the graph, or
     * creates a new one. Always ensures a {@link PropertyReadEdge} from {@code parent} to the node.
     */
    private static PropertyNode findOrCreatePropertyNode(
            final DefaultDirectedGraph<ValueNode, ValueEdge> graph,
            final ValueNode parent,
            final String name,
            final ReadAccessor accessor) {

        final var candidate = new PropertyNode(name, accessor.getType());
        if (!graph.containsVertex(candidate)) {
            graph.addVertex(candidate);
        }
        if (!graph.containsEdge(parent, candidate)) {
            graph.addEdge(parent, candidate, new PropertyReadEdge(accessor.template()));
        }
        return candidate;
    }

    /**
     * Returns the existing {@link TypedValueNode} or {@link TargetSlotNode} for {@code type}
     * from the type index, or creates a fresh {@link TypedValueNode} and registers it in both
     * the graph and the index.
     *
     * <p>If the index entry is a {@link PropertyNode} or {@link SourceParamNode} (i.e. a source
     * node, not a transform intermediary), a fresh {@link TypedValueNode} is created and
     * <em>replaces</em> the source-node entry in the index. This prevents self-loops when a
     * strategy's required-input type coincides with a source property's type.
     */
    private static ValueNode getOrCreateTypedNode(
            final DefaultDirectedGraph<ValueNode, ValueEdge> graph,
            final Map<String, ValueNode> typeIndex,
            final TypeMirror type) {
        final ValueNode existing = typeIndex.get(type.toString());
        if (existing instanceof TypedValueNode || existing instanceof TargetSlotNode) {
            return existing;
        }
        // existing is null, PropertyNode, or SourceParamNode — create a TypedValueNode
        final var node = new TypedValueNode(type, type.toString());
        typeIndex.put(type.toString(), node);
        graph.addVertex(node);
        return node;
    }

    /**
     * Returns the vertex set in source-then-target-reachability order, so that forward-direction
     * edges are proposed before their reverse counterparts during the fixpoint double-loop.
     *
     * <p>This is a proposal-ordering heuristic, not a correctness guarantee: cycles between
     * inverse strategy pairs are allowed, and downstream stages traverse only resolved
     * {@code GraphPath}s. The ordering still tends to produce shorter resolved paths by
     * proposing forward edges before reverse ones.
     *
     * <p>Ordering is a stable partition over three classes:
     * <ol>
     *   <li>{@code sourceLeaf} + vertices reachable from it (forward side).</li>
     *   <li>Orphans (neither reachable from source nor reaching target).</li>
     *   <li>Vertices that reach {@code targetSlot} + {@code targetSlot} itself (target side).</li>
     * </ol>
     *
     * <p>Within each class, original insertion order is preserved.
     */
    private static List<ValueNode> sortBySourceTargetReachability(
            final DefaultDirectedGraph<ValueNode, ValueEdge> graph,
            final ValueNode sourceLeaf,
            final TargetSlotNode targetSlot) {
        final var forward = new BFSShortestPath<>(graph);
        final var vertices = List.copyOf(graph.vertexSet());
        final var sourceSide = new ArrayList<ValueNode>();
        final var orphans = new ArrayList<ValueNode>();
        final var targetSide = new ArrayList<ValueNode>();

        for (final var v : vertices) {
            final boolean reachableFromSource = v.equals(sourceLeaf)
                    || (graph.containsVertex(sourceLeaf) && forward.getPath(sourceLeaf, v) != null);
            final boolean reachesTarget = v.equals(targetSlot)
                    || (graph.containsVertex(targetSlot) && forward.getPath(v, targetSlot) != null);

            if (reachableFromSource && !reachesTarget) {
                sourceSide.add(v);
            } else if (reachesTarget && !reachableFromSource) {
                targetSide.add(v);
            } else if (reachableFromSource) {
                // Both — goes to source side (closer to source takes precedence).
                sourceSide.add(v);
            } else {
                orphans.add(v);
            }
        }

        final var ordered = new ArrayList<ValueNode>(vertices.size());
        ordered.addAll(sourceSide);
        ordered.addAll(orphans);
        ordered.addAll(targetSide);
        return ordered;
    }

    /**
     * Asserts the graph invariants defined in {@code value-graph/spec.md}. Throws
     * {@link IllegalStateException} on any violation.
     */
    private static void assertInvariants(final DefaultDirectedGraph<ValueNode, ValueEdge> graph) {

        for (final var vertex : graph.vertexSet()) {
            if (vertex instanceof TargetSlotNode
                    && !graph.outgoingEdgesOf(vertex).isEmpty()) {
                throw new IllegalStateException(
                        "TargetSlotNode " + vertex + " has outgoing edges — invariant violated");
            }
        }

        for (final var edge : graph.edgeSet()) {
            if (edge instanceof PropertyReadEdge) {
                final var src = graph.getEdgeSource(edge);
                final var tgt = graph.getEdgeTarget(edge);
                if (!(src instanceof SourceParamNode) && !(src instanceof PropertyNode)) {
                    throw new IllegalStateException(
                            "PropertyReadEdge source must be SourceParamNode or PropertyNode, got: " + src);
                }
                if (!(tgt instanceof PropertyNode)) {
                    throw new IllegalStateException("PropertyReadEdge target must be PropertyNode, got: " + tgt);
                }
            }
            if (edge instanceof LiftEdge) {
                final var liftEdge = (LiftEdge) edge;
                if (!graph.containsVertex(liftEdge.getInnerInputNode())) {
                    throw new IllegalStateException("LiftEdge innerInputNode " + liftEdge.getInnerInputNode()
                            + " not present in parent ValueGraph");
                }
                if (!graph.containsVertex(liftEdge.getInnerOutputNode())) {
                    throw new IllegalStateException("LiftEdge innerOutputNode " + liftEdge.getInnerOutputNode()
                            + " not present in parent ValueGraph");
                }
            }
            if (edge instanceof TypeTransformEdge) {
                if (((TypeTransformEdge) edge).getCodeTemplate() == null) {
                    throw new IllegalStateException(
                            "TypeTransformEdge must carry a non-null codeTemplate at construction: " + edge);
                }
            }
        }
    }

    private Map<String, ReadAccessor> discoverSourcePropertyMap(final TypeMirror type) {
        final Map<String, ReadAccessor> merged = new LinkedHashMap<>();
        final Map<String, Integer> priorities = new LinkedHashMap<>();
        for (final var strategy : sourceDiscoveries) {
            for (final var accessor : strategy.discover(type, elements, types)) {
                final int current = priorities.getOrDefault(accessor.getName(), Integer.MIN_VALUE);
                if (strategy.priority() > current) {
                    merged.put(accessor.getName(), accessor);
                    priorities.put(accessor.getName(), strategy.priority());
                }
            }
        }
        return merged;
    }

    private Map<String, WriteAccessor> discoverTargetPropertyMap(final TypeMirror type) {
        final Map<String, WriteAccessor> merged = new LinkedHashMap<>();
        final Map<String, Integer> priorities = new LinkedHashMap<>();
        for (final var strategy : targetDiscoveries) {
            for (final var accessor : strategy.discover(type, elements, types)) {
                final int current = priorities.getOrDefault(accessor.getName(), Integer.MIN_VALUE);
                if (strategy.priority() > current) {
                    merged.put(accessor.getName(), accessor);
                    priorities.put(accessor.getName(), strategy.priority());
                }
            }
        }
        return merged;
    }

    private static <T> List<T> loadServices(final Class<T> serviceClass) {
        final var loader = ServiceLoader.load(serviceClass, serviceClass.getClassLoader());
        final List<T> result = new ArrayList<>();
        loader.forEach(result::add);
        return List.copyOf(result);
    }

    private static <T> List<T> loadAndSortByPriority(
            final Class<T> serviceClass, final java.util.function.ToIntFunction<T> priorityFn) {
        return ServiceLoader.load(serviceClass, serviceClass.getClassLoader()).stream()
                .map(ServiceLoader.Provider::get)
                .sorted(comparingInt((T s) -> -priorityFn.applyAsInt(s)))
                .collect(toUnmodifiableList());
    }
}
