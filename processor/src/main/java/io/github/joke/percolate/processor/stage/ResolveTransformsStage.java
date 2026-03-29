package io.github.joke.percolate.processor.stage;

import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.graph.MappingGraph;
import io.github.joke.percolate.processor.graph.SourcePropertyNode;
import io.github.joke.percolate.processor.graph.TargetPropertyNode;
import io.github.joke.percolate.processor.graph.TransformEdge;
import io.github.joke.percolate.processor.graph.TypeNode;
import io.github.joke.percolate.processor.model.DiscoveredMethod;
import io.github.joke.percolate.processor.spi.ResolutionContext;
import io.github.joke.percolate.processor.spi.TypeTransformStrategy;
import io.github.joke.percolate.processor.transform.CodeTemplate;
import io.github.joke.percolate.processor.transform.ResolvedMapping;
import io.github.joke.percolate.processor.transform.ResolvedModel;
import io.github.joke.percolate.processor.transform.TransformProposal;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.BFSShortestPath;
import org.jgrapht.graph.DefaultDirectedGraph;

public final class ResolveTransformsStage {

    private static final int MAX_ITERATIONS = 30;

    private final Types types;
    private final Elements elements;
    private final List<TypeTransformStrategy> strategies;

    @Inject
    ResolveTransformsStage(final Types types, final Elements elements) {
        this.types = types;
        this.elements = elements;
        this.strategies = loadStrategies();
    }

    ResolveTransformsStage(final Types types, final Elements elements,
            final List<TypeTransformStrategy> strategies) {
        this.types = types;
        this.elements = elements;
        this.strategies = strategies;
    }

    public StageResult<ResolvedModel> execute(final MappingGraph mappingGraph) {
        final Map<DiscoveredMethod, List<ResolvedMapping>> methodMappings = new LinkedHashMap<>();

        for (final var entry : mappingGraph.getMethodGraphs().entrySet()) {
            final var method = entry.getKey();
            final var graph = entry.getValue();
            final List<ResolvedMapping> resolved = new ArrayList<>();
            final var ctx = new ResolutionContext(types, elements, mappingGraph.getMethods(), method);

            for (final var edge : graph.edgeSet()) {
                final var sourceNode = (SourcePropertyNode) graph.getEdgeSource(edge);
                final var targetNode = (TargetPropertyNode) graph.getEdgeTarget(edge);
                final var sourceType = sourceNode.getType();
                final var targetType = targetNode.getType();

                final var path = resolveTransformPath(sourceType, targetType, ctx);
                resolved.add(new ResolvedMapping(sourceNode, targetNode, path));
            }

            methodMappings.put(method, List.copyOf(resolved));
        }

        return StageResult.success(
                new ResolvedModel(mappingGraph.getMapperType(), mappingGraph.getMethods(), methodMappings));
    }

    @SuppressWarnings("NullAway")
    private GraphPath<TypeNode, TransformEdge> resolveTransformPath(
            final TypeMirror sourceType, final TypeMirror targetType, final ResolutionContext ctx) {

        final var graph = new DefaultDirectedGraph<TypeNode, TransformEdge>(TransformEdge.class);
        final var sourceNode = new TypeNode(sourceType, sourceType.toString());
        final var targetNode = new TypeNode(targetType, targetType.toString());
        graph.addVertex(sourceNode);
        graph.addVertex(targetNode);

        final Map<String, TypeNode> nodeIndex = new LinkedHashMap<>();
        nodeIndex.put(sourceType.toString(), sourceNode);
        nodeIndex.put(targetType.toString(), targetNode);

        for (var iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            var expanded = false;

            final var currentNodes = List.copyOf(graph.vertexSet());
            for (final var from : currentNodes) {
                for (final var to : currentNodes) {
                    if (from.equals(to)) {
                        continue;
                    }

                    for (final var strategy : strategies) {
                        final var proposal = strategy.canProduce(from.getType(), to.getType(), ctx);
                        if (proposal.isEmpty()) {
                            continue;
                        }

                        final var prop = proposal.get();
                        final var inputNode = getOrCreateNode(graph, nodeIndex, prop.getRequiredInput());
                        final var outputNode = getOrCreateNode(graph, nodeIndex, prop.getProducedOutput());

                        if (!graph.containsEdge(inputNode, outputNode)) {
                            final var codeTemplate = resolveCodeTemplate(prop, ctx);
                            graph.addEdge(inputNode, outputNode, new TransformEdge(strategy, codeTemplate));
                            expanded = true;
                        }
                    }
                }
            }

            final var pathResult = new BFSShortestPath<>(graph).getPath(sourceNode, targetNode);
            if (pathResult != null) {
                return pathResult;
            }

            if (!expanded) {
                System.err.println("DEBUG no expansion, stopping");
                break;
            }
        }

        return null;
    }

    private CodeTemplate resolveCodeTemplate(final TransformProposal proposal, final ResolutionContext ctx) {
        if (proposal.getElementConstraint() == null || proposal.getTemplateComposer() == null) {
            return proposal.getCodeTemplate();
        }

        final var constraint = proposal.getElementConstraint();
        final var innerPath = resolveTransformPath(constraint.getFromType(), constraint.getToType(), ctx);

        if (innerPath == null) {
            return proposal.getCodeTemplate();
        }

        final CodeTemplate innerTemplate = composeTemplates(innerPath.getEdgeList());
        return proposal.getTemplateComposer().apply(innerTemplate);
    }

    private static CodeTemplate composeTemplates(final List<TransformEdge> edges) {
        return input -> {
            var result = input;
            for (final var edge : edges) {
                result = edge.getCodeTemplate().apply(result);
            }
            return result;
        };
    }

    private static TypeNode getOrCreateNode(final DefaultDirectedGraph<TypeNode, TransformEdge> graph,
            final Map<String, TypeNode> nodeIndex, final TypeMirror type) {
        final var key = type.toString();
        return nodeIndex.computeIfAbsent(key, k -> {
            final var node = new TypeNode(type, key);
            graph.addVertex(node);
            return node;
        });
    }

    private static List<TypeTransformStrategy> loadStrategies() {
        final var loader = ServiceLoader.load(TypeTransformStrategy.class,
                TypeTransformStrategy.class.getClassLoader());
        final List<TypeTransformStrategy> result = new ArrayList<>();
        loader.forEach(result::add);
        return List.copyOf(result);
    }
}
