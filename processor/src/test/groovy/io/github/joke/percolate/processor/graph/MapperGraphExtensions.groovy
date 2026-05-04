package io.github.joke.percolate.processor.graph

import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.processor.graph.Node
import io.github.joke.percolate.processor.graph.Edge
import io.github.joke.percolate.processor.graph.Scope

class MapperGraphExtensions {

    static void hasNode(MapperGraph graph, Map<String, Object> criteria) {
        def predicate = buildNodePredicate(criteria)
        assert graph.nodes.any(predicate) : "graph has node matching " + criteria
    }

    static void hasEdge(MapperGraph graph, Map<String, Object> criteria) {
        def predicate = buildEdgePredicate(criteria)
        assert graph.edges.any(predicate) : "graph has edge matching " + criteria
    }

    static void hasNoEdges(MapperGraph graph) {
        assert graph.edgeCount == 0 : "graph has no edges"
    }

    static List<Node> nodesIn(MapperGraph graph, Scope scope) {
        graph.nodesByScope(scope).toList()
    }

    static int nodeCount(MapperGraph graph) {
        graph.nodeCount()
    }

    static int edgeCount(MapperGraph graph) {
        graph.edgeCount()
    }

    static void isAcyclic(MapperGraph graph) {
        assert graph.isAcyclic() : "graph is acyclic"
    }

    static List<String> nodeIds(MapperGraph graph) {
        graph.nodes.map { it.id }.toList()
    }

    static Scope scope(MapperGraph graph, String scopeEncoding) {
        def found = graph.nodes.filter { it.scope.encode == scopeEncoding }.findFirst()
        assert found.isPresent : "graph has node with scope encoding '$scopeEncoding'"
        return found.get.scope
    }

    static List<Scope> scopes(MapperGraph graph) {
        graph.nodes.map { it.scope }.distinct().toList()
    }

    static List<Edge> realisedEdgesFrom(MapperGraph graph, Node node) {
        graph.edges.filter { it.kind == EdgeKind.REALISED && it.from == node }.toList()
    }

    static List<Edge> markersOn(MapperGraph graph, Node node) {
        graph.edges.filter { it.kind == EdgeKind.MARKER && it.from == node }.toList()
    }

    static io.github.joke.percolate.processor.graph.GroupCodegen groupCodegenOf(MapperGraph graph, String groupId) {
        graph.groupCodegen(groupId).orElse(null)
    }

    static MapperGraph subGraphView(MapperGraph graph, io.github.joke.percolate.processor.graph.EdgeKind... kinds) {
        def subgraph = new io.github.joke.percolate.processor.graph.MapperGraph()
        for (def node in graph.nodes.toList()) {
            subgraph.addNode(node)
        }
        for (def edge in graph.edges.toList()) {
            if (kinds.any { it == edge.kind }) {
                subgraph.addEdge(edge)
            }
        }
        return subgraph
    }

    static org.jgrapht.alg.cycle.CycleDetector<io.github.joke.percolate.processor.graph.Node, io.github.joke.percolate.processor.graph.Edge> cycleDetector(MapperGraph graph) {
        return new org.jgrapht.alg.cycle.CycleDetector<>(graph.graph)
    }

    private static buildNodePredicate(Map<String, Object> criteria) {
        return { Node node ->
            criteria.entrySet().every { entry ->
                def key = entry.key
                def value = entry.value
                switch (key) {
                    case 'scope':
                        return node.scope == value
                    case 'hasType':
                        return value ? node.type.isPresent() : node.type.isEmpty()
                    case 'isSource':
                        return node.loc instanceof io.github.joke.percolate.processor.graph.SourceLocation
                    case 'isTarget':
                        return node.loc instanceof io.github.joke.percolate.processor.graph.TargetLocation
                    case 'type':
                        if (value == null) return !node.type.isPresent()
                        return node.type.map { it.toString() }.orElse('') == value.toString()
                    case 'path':
                        if (node.loc instanceof SourceLocation) {
                            return node.loc.path.toString() == value.toString()
                        }
                        if (node.loc instanceof TargetLocation) {
                            return node.loc.path.toString() == value.toString()
                        }
                        return false
                    case 'id':
                        return node.id == value.toString()
                    default:
                        return false
                }
            }
        }
    }

    private static buildEdgePredicate(Map<String, Object> criteria) {
        return { Edge edge ->
            criteria.entrySet().every { entry ->
                def key = entry.key
                def value = entry.value
                switch (key) {
                    case 'fromScope':
                        return edge.from.scope == value
                    case 'toScope':
                        return edge.to.scope == value
                    case 'weight':
                        return edge.weight == value
                    case 'hasDirective':
                        return value ? edge.directive.isPresent() : !edge.directive.isPresent()
                    case 'fromPath':
                        if (edge.from.loc instanceof SourceLocation) {
                            return edge.from.loc.path.toString() == value.toString()
                        }
                        return false
                    case 'toPath':
                        if (edge.to.loc instanceof SourceLocation) {
                            return edge.to.loc.path.toString() == value.toString()
                        }
                        if (edge.to.loc instanceof TargetLocation) {
                            return edge.to.loc.path.toString() == value.toString()
                        }
                        return false
                    default:
                        return false
                }
            }
        }
    }
}
