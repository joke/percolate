package io.github.joke.percolate.processor.stage;

import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.graph.SourceParamNode;
import io.github.joke.percolate.processor.graph.TargetSlotNode;
import io.github.joke.percolate.processor.graph.ValueEdge;
import io.github.joke.percolate.processor.graph.ValueGraphResult;
import io.github.joke.percolate.processor.graph.ValueNode;
import io.github.joke.percolate.processor.match.MappingAssignment;
import io.github.joke.percolate.processor.match.MethodMatching;
import io.github.joke.percolate.processor.match.ResolutionFailure;
import io.github.joke.percolate.processor.match.ResolvedAssignment;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;
import org.jgrapht.alg.shortestpath.BFSShortestPath;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jspecify.annotations.Nullable;

/**
 * Runs a {@code BFSShortestPath} over each method's {@code ValueGraph} to resolve the shortest
 * path from the source {@link SourceParamNode} to the target {@link TargetSlotNode} for every
 * {@link MappingAssignment}.
 *
 * <p>This stage does NOT walk access chains or construct {@code ReadAccessor}s. It consumes the
 * graph built by {@code BuildValueGraphStage} and emits one {@link ResolvedAssignment} per
 * assignment. Code templates for non-{@code LiftEdge} edges are materialised at construction in
 * {@code BuildValueGraphStage}; {@code LiftEdge} templates compose on demand inside
 * {@code GenerateStage}.
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public final class ResolvePathStage {

    public StageResult<Map<MethodMatching, List<ResolvedAssignment>>> execute(final ValueGraphResult valueGraphResult) {

        final Map<MethodMatching, List<ResolvedAssignment>> result = new LinkedHashMap<>();

        for (final var entry : valueGraphResult.getGraphs().entrySet()) {
            final var matching = entry.getKey();
            final var graph = entry.getValue();
            final var assignments = resolveMethod(matching, graph, valueGraphResult.getResolutionFailures());
            result.put(matching, List.copyOf(assignments));
        }

        return StageResult.success(Map.copyOf(result));
    }

    private static List<ResolvedAssignment> resolveMethod(
            final MethodMatching matching,
            final DefaultDirectedGraph<ValueNode, ValueEdge> graph,
            final Map<MappingAssignment, ResolutionFailure> resolutionFailures) {

        // Index SourceParamNodes and TargetSlotNodes by name for O(1) lookup
        final Map<String, SourceParamNode> paramNodes = new LinkedHashMap<>();
        final Map<String, TargetSlotNode> slotNodes = new LinkedHashMap<>();
        for (final var vertex : graph.vertexSet()) {
            if (vertex instanceof SourceParamNode) {
                final var spn = (SourceParamNode) vertex;
                paramNodes.put(spn.getName(), spn);
            } else if (vertex instanceof TargetSlotNode) {
                final var tsn = (TargetSlotNode) vertex;
                slotNodes.put(tsn.getName(), tsn);
            }
        }

        final boolean multiParam = matching.getMethod().getParameters().size() > 1;
        final var bfs = new BFSShortestPath<>(graph);
        final List<ResolvedAssignment> resolved = new ArrayList<>();

        for (final var assignment : matching.getAssignments()) {
            resolved.add(resolveAssignment(assignment, multiParam, paramNodes, slotNodes, bfs, resolutionFailures));
        }

        return resolved;
    }

    private static ResolvedAssignment resolveAssignment(
            final MappingAssignment assignment,
            final boolean multiParam,
            final Map<String, SourceParamNode> paramNodes,
            final Map<String, TargetSlotNode> slotNodes,
            final BFSShortestPath<ValueNode, ValueEdge> bfs,
            final Map<MappingAssignment, ResolutionFailure> resolutionFailures) {

        // If BuildValueGraphStage recorded an access-chain failure, propagate it directly
        final @Nullable ResolutionFailure chainFailure = resolutionFailures.get(assignment);
        if (chainFailure != null) {
            return new ResolvedAssignment(assignment, null, chainFailure);
        }

        // Locate the starting SourceParamNode for this assignment
        final @Nullable SourceParamNode startParam = findStartParam(assignment, multiParam, paramNodes);
        if (startParam == null) {
            return new ResolvedAssignment(assignment, null, null);
        }

        // Locate the TargetSlotNode
        final @Nullable TargetSlotNode targetSlot = slotNodes.get(assignment.getTargetName());
        if (targetSlot == null) {
            return new ResolvedAssignment(assignment, null, null);
        }

        // BFS shortest path in the ValueGraph
        return new ResolvedAssignment(assignment, bfs.getPath(startParam, targetSlot), null);
    }

    @Nullable
    private static SourceParamNode findStartParam(
            final MappingAssignment assignment,
            final boolean multiParam,
            final Map<String, SourceParamNode> paramNodes) {

        if (multiParam) {
            final var sourcePath = assignment.getSourcePath();
            if (sourcePath.isEmpty()) {
                return null;
            }
            return paramNodes.get(sourcePath.get(0));
        }
        // Single-parameter method: exactly one SourceParamNode
        return paramNodes.values().stream().findFirst().orElse(null);
    }
}
