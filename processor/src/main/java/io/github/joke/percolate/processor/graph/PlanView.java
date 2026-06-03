package io.github.joke.percolate.processor.graph;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jgrapht.Graph;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.AsWeightedGraph;
import org.jgrapht.graph.MaskSubgraph;

/**
 * A {@link GraphSource} exposing only the edges of the chosen plan: {@code REALISED} edges that
 * belong to a {@code SAT}-outcome group, with multi-fire OR-choices resolved to the cheapest branch.
 * Dijkstra serves as a cost oracle ({@code d(n)} = cheapest source-to-{@code n} cost); the plan
 * itself is assembled by a target-to-source walk that keeps all slots of AND nodes and the cheapest
 * group at OR nodes.
 */
public final class PlanView implements GraphSource {

    private static final String SEED_PACKAGE_PREFIX = "io.github.joke.percolate.processor.stages.seed.";

    private final MaskSubgraph<Node, Edge> subgraph;
    private final MapperGraph mapperGraph;
    private final List<ExpansionGroup> planGroups;
    private final Set<Node> incidentNodes;

    private PlanView(
            final MaskSubgraph<Node, Edge> subgraph,
            final MapperGraph mapperGraph,
            final List<ExpansionGroup> planGroups,
            final Set<Node> incidentNodes) {
        this.subgraph = subgraph;
        this.mapperGraph = mapperGraph;
        this.planGroups = planGroups;
        this.incidentNodes = incidentNodes;
    }

    public static PlanView of(final MapperGraph graph) {
        final var underlying = graph.underlyingGraph();
        final var allGroups = graph.groups().collect(toUnmodifiableList());
        final var satGroups = graph.groupOutcomes()
                .filter(o -> o.getKind() == GroupOutcome.Kind.SAT)
                .map(GroupOutcome::getGroup)
                .collect(toUnmodifiableList());

        // Keep every REALISED edge except those owned solely by UNSAT groups (dead multi-fire siblings).
        final var keep = underlying.edgeSet().stream()
                .filter(e -> e.getKind() == EdgeKind.REALISED)
                .filter(e -> keepEdge(e, allGroups, satGroups))
                .collect(toCollection(HashSet::new));

        // Resolve OR-choices at true multi-fire roots: only NON-SEED bridge groups compete. Seed-registered
        // scaffolding (path-segment / target-chain / directive-binding) co-roots with the real producer and
        // must never be pruned. At a node rooted by more than one competing group, drop the losers' edges.
        final var cost = costToSource(underlying, keep);
        final var byRoot = new HashMap<Node, List<ExpansionGroup>>();
        satGroups.stream().filter(PlanView::isBridgeGroup).forEach(g -> byRoot.computeIfAbsent(
                        g.getRoot(), k -> new ArrayList<>())
                .add(g));
        final var loserEdges = byRoot.values().stream()
                .filter(groups -> groups.size() > 1)
                .flatMap(groups -> {
                    final var chosen = cheapest(groups, keep, cost);
                    return groups.stream().filter(g -> g != chosen).flatMap(loser -> groupEdges(loser, keep));
                })
                .collect(toCollection(HashSet::new));
        keep.removeAll(loserEdges);

        // Reachability-filter from each return-root so disconnected loser/dead subtrees drop out.
        final var planEdges = reachableEdges(underlying, keep);
        final var incident = planEdges.stream()
                .flatMap(e -> Stream.of(e.getFrom(), e.getTo()))
                .collect(toCollection(HashSet::new));
        final var planGroups = satGroups.stream()
                .filter(g -> incident.contains(g.getRoot()) && incident.containsAll(g.getSlots()))
                .collect(toUnmodifiableList());

        final var mask = new MaskSubgraph<>(underlying, (Node v) -> false, (Edge e) -> !planEdges.contains(e));
        return new PlanView(mask, graph, planGroups, Collections.unmodifiableSet(incident));
    }

    private static boolean isBridgeGroup(final ExpansionGroup group) {
        return !group.getStrategyClassFqn().startsWith(SEED_PACKAGE_PREFIX);
    }

    private static boolean keepEdge(
            final Edge edge, final List<ExpansionGroup> allGroups, final List<ExpansionGroup> satGroups) {
        final var hasGroup = allGroups.stream().anyMatch(g -> g.contains(edge));
        final var inSatGroup = satGroups.stream().anyMatch(g -> g.contains(edge));
        return !hasGroup || inSatGroup;
    }

    private static Set<Edge> reachableEdges(final Graph<Node, Edge> graph, final Set<Edge> keep) {
        final var planEdges = new HashSet<Edge>();
        final var queue = new ArrayDeque<Node>();
        final var seen = new HashSet<Node>();
        graph.vertexSet().stream().filter(PlanView::isReturnRoot).forEach(root -> {
            if (seen.add(root)) {
                queue.add(root);
            }
        });
        while (!queue.isEmpty()) {
            final var node = queue.remove();
            for (final var edge : graph.incomingEdgesOf(node)) {
                if (!keep.contains(edge)) {
                    continue;
                }
                planEdges.add(edge);
                if (seen.add(edge.getFrom())) {
                    queue.add(edge.getFrom());
                }
            }
        }
        return planEdges;
    }

    @SuppressWarnings("PMD.UseConcurrentHashMap") // local, single-threaded Dijkstra weighting map
    private static Map<Node, Double> costToSource(final Graph<Node, Edge> graph, final Set<Edge> eligible) {
        final var maskEligible = new MaskSubgraph<>(graph, (Node v) -> false, (Edge e) -> !eligible.contains(e));
        final Map<Edge, Double> weights = new HashMap<>();
        eligible.forEach(e -> weights.put(e, (double) e.getWeight()));
        final var weighted = new AsWeightedGraph<>(maskEligible, weights);
        final var sources = maskEligible.vertexSet().stream()
                .filter(n -> maskEligible.incomingEdgesOf(n).isEmpty())
                .collect(toUnmodifiableList());
        final var best = new HashMap<Node, Double>();
        for (final var source : sources) {
            final var paths = new DijkstraShortestPath<>(weighted).getPaths(source);
            for (final var node : maskEligible.vertexSet()) {
                best.merge(node, paths.getWeight(node), Math::min);
            }
        }
        return best;
    }

    private static ExpansionGroup cheapest(
            final List<ExpansionGroup> groups, final Set<Edge> eligible, final Map<Node, Double> cost) {
        return groups.stream()
                .min(Comparator.<ExpansionGroup>comparingDouble(g -> groupCost(g, eligible, cost))
                        .thenComparing(g -> g.getSlots().stream()
                                .map(Node::id)
                                .sorted()
                                .findFirst()
                                .orElse(""))
                        .thenComparing(ExpansionGroup::getStrategyClassFqn))
                .orElseThrow();
    }

    private static double groupCost(
            final ExpansionGroup group, final Set<Edge> eligible, final Map<Node, Double> cost) {
        return group.getSlots().stream()
                .mapToDouble(slot ->
                        minSlotEdgeWeight(group, slot, eligible) + cost.getOrDefault(slot, Double.POSITIVE_INFINITY))
                .sum();
    }

    private static double minSlotEdgeWeight(final ExpansionGroup group, final Node slot, final Set<Edge> eligible) {
        return slotEdges(group, slot, eligible).mapToInt(Edge::getWeight).min().orElse(0);
    }

    private static Stream<Edge> groupEdges(final ExpansionGroup group, final Set<Edge> edges) {
        return group.getSlots().stream().flatMap(slot -> slotEdges(group, slot, edges));
    }

    private static Stream<Edge> slotEdges(final ExpansionGroup group, final Node slot, final Set<Edge> eligible) {
        return eligible.stream()
                .filter(e -> e.getFrom().equals(slot) && e.getTo().equals(group.getRoot()))
                .filter(group::contains);
    }

    private static boolean isReturnRoot(final Node node) {
        return node.getLoc() instanceof TargetLocation
                && ((TargetLocation) node.getLoc()).getPath().getSegments().isEmpty();
    }

    @Override
    public Stream<Node> nodes() {
        return incidentNodes.stream().sorted(Comparator.comparing(Node::id));
    }

    @Override
    public Stream<Edge> edges() {
        return subgraph.edgeSet().stream().sorted();
    }

    public Stream<Node> nodesByScope(final Scope scope) {
        return nodes().filter(n -> n.getScope().equals(scope));
    }

    public Stream<ExpansionGroup> groups() {
        return planGroups.stream();
    }

    public MapperGraph delegate() {
        return mapperGraph;
    }
}
