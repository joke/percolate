package io.github.joke.percolate.processor.stage;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.graph.AccessEdge;
import io.github.joke.percolate.processor.graph.MappingEdge;
import io.github.joke.percolate.processor.graph.MappingGraph;
import io.github.joke.percolate.processor.graph.SourcePropertyNode;
import io.github.joke.percolate.processor.graph.SourceRootNode;
import io.github.joke.percolate.processor.graph.TargetPropertyNode;
import io.github.joke.percolate.processor.graph.TransformEdge;
import io.github.joke.percolate.processor.graph.TypeNode;
import io.github.joke.percolate.processor.model.MappingMethodModel;
import io.github.joke.percolate.processor.model.ReadAccessor;
import io.github.joke.percolate.processor.model.WriteAccessor;
import io.github.joke.percolate.processor.spi.ResolutionContext;
import io.github.joke.percolate.processor.spi.SourcePropertyDiscovery;
import io.github.joke.percolate.processor.spi.TargetPropertyDiscovery;
import io.github.joke.percolate.processor.spi.TypeTransformStrategy;
import io.github.joke.percolate.processor.transform.AccessResolutionFailure;
import io.github.joke.percolate.processor.transform.CodeTemplate;
import io.github.joke.percolate.processor.transform.ResolvedMapping;
import io.github.joke.percolate.processor.transform.ResolvedModel;
import io.github.joke.percolate.processor.transform.TransformProposal;
import io.github.joke.percolate.processor.transform.TransformResolution;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.ToIntFunction;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.BFSShortestPath;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jspecify.annotations.Nullable;

public final class ResolveTransformsStage {

    private static final int MAX_ITERATIONS = 30;

    private final Types types;
    private final Elements elements;
    private final List<TypeTransformStrategy> strategies;
    private final List<SourcePropertyDiscovery> sourceDiscoveries;
    private final List<TargetPropertyDiscovery> targetDiscoveries;

    @Inject
    ResolveTransformsStage(final Types types, final Elements elements) {
        this.types = types;
        this.elements = elements;
        this.strategies = loadServices(TypeTransformStrategy.class);
        this.sourceDiscoveries =
                loadAndSortByPriority(SourcePropertyDiscovery.class, SourcePropertyDiscovery::priority);
        this.targetDiscoveries =
                loadAndSortByPriority(TargetPropertyDiscovery.class, TargetPropertyDiscovery::priority);
    }

    ResolveTransformsStage(
            final Types types,
            final Elements elements,
            final List<TypeTransformStrategy> strategies,
            final List<SourcePropertyDiscovery> sourceDiscoveries,
            final List<TargetPropertyDiscovery> targetDiscoveries) {
        this.types = types;
        this.elements = elements;
        this.strategies = strategies;
        this.sourceDiscoveries = sourceDiscoveries;
        this.targetDiscoveries = targetDiscoveries;
    }

    public StageResult<ResolvedModel> execute(final MappingGraph mappingGraph) {
        final Map<MappingMethodModel, List<ResolvedMapping>> methodMappings = new LinkedHashMap<>();
        final Map<MappingMethodModel, Set<String>> unmappedTargets = new LinkedHashMap<>();
        final Map<MappingMethodModel, Map<String, Set<String>>> duplicateTargets = new LinkedHashMap<>();

        for (final var entry : mappingGraph.getMethodGraphs().entrySet()) {
            final var method = (MappingMethodModel) entry.getKey();
            @SuppressWarnings("unchecked")
            final var graph = (DefaultDirectedGraph<Object, Object>) entry.getValue();
            final var ctx = new ResolutionContext(types, elements, mappingGraph.getMapperType(), method.getMethod());

            final var mappings = new ArrayList<ResolvedMapping>();
            final var unmapped = new LinkedHashSet<String>();
            final var duplicates = new LinkedHashMap<String, Set<String>>();

            resolveMethod(method, graph, ctx, mappings, unmapped, duplicates);

            methodMappings.put(method, List.copyOf(mappings));
            unmappedTargets.put(method, Set.copyOf(unmapped));
            duplicateTargets.put(method, Map.copyOf(duplicates));
        }

        return StageResult.success(new ResolvedModel(
                mappingGraph.getMapperType(),
                mappingGraph.getMethods(),
                methodMappings,
                unmappedTargets,
                duplicateTargets));
    }

    private void resolveMethod(
            final MappingMethodModel method,
            final DefaultDirectedGraph<Object, Object> graph,
            final ResolutionContext ctx,
            final List<ResolvedMapping> mappings,
            final Set<String> unmapped,
            final Map<String, Set<String>> duplicates) {

        final var sourceRoot = findSourceRoot(graph);
        if (sourceRoot == null) {
            return;
        }

        final var targetWriteAccessors = discoverTargetProperties(method.getTargetType());

        for (final var vertex : graph.vertexSet()) {
            if (!(vertex instanceof TargetPropertyNode)) {
                continue;
            }
            final var targetNode = (TargetPropertyNode) vertex;
            final var incomingMappingEdges = graph.incomingEdgesOf(targetNode).stream()
                    .filter(e -> e instanceof MappingEdge)
                    .collect(toUnmodifiableList());

            if (incomingMappingEdges.isEmpty()) {
                unmapped.add(targetNode.getName());
                continue;
            }

            if (incomingMappingEdges.size() > 1) {
                final var sources = incomingMappingEdges.stream()
                        .map(e -> ((SourcePropertyNode) graph.getEdgeSource(e)).getName())
                        .collect(toUnmodifiableSet());
                duplicates.put(targetNode.getName(), sources);
                continue;
            }

            final var mappingEdge = incomingMappingEdges.get(0);
            final var sourceLeaf = (SourcePropertyNode) graph.getEdgeSource(mappingEdge);
            final var sourceName = buildChainPath(graph, sourceRoot, sourceLeaf);

            resolveMapping(
                    method, graph, ctx, sourceRoot, sourceLeaf, sourceName, targetNode, targetWriteAccessors, mappings);
        }
    }

    private void resolveMapping(
            final MappingMethodModel method,
            final DefaultDirectedGraph<Object, Object> graph,
            final ResolutionContext ctx,
            final SourceRootNode sourceRoot,
            final SourcePropertyNode sourceLeaf,
            final String sourceName,
            final TargetPropertyNode targetNode,
            final Map<String, WriteAccessor> targetWriteAccessors,
            final List<ResolvedMapping> mappings) {

        final var chainResult = buildAccessorChain(graph, sourceRoot, sourceLeaf, method.getSourceType());

        if (chainResult.failure != null) {
            mappings.add(
                    new ResolvedMapping(List.of(), sourceName, null, targetNode.getName(), null, chainResult.failure));
            return;
        }

        final var targetAccessor = targetWriteAccessors.get(targetNode.getName());
        if (targetAccessor == null) {
            final var failure = new AccessResolutionFailure(
                    targetNode.getName(),
                    0,
                    targetNode.getName(),
                    method.getTargetType(),
                    targetWriteAccessors.keySet());
            mappings.add(
                    new ResolvedMapping(chainResult.accessors, sourceName, null, targetNode.getName(), null, failure));
            return;
        }

        @SuppressWarnings("NullAway") // resolvedType is non-null in success path
        final var resolution = resolveTransformPath(chainResult.resolvedType, targetAccessor.getType(), ctx);
        mappings.add(new ResolvedMapping(
                chainResult.accessors, sourceName, targetAccessor, targetNode.getName(), resolution, null));
    }

    private AccessorChainResult buildAccessorChain(
            final DefaultDirectedGraph<Object, Object> graph,
            final SourceRootNode sourceRoot,
            final SourcePropertyNode targetLeaf,
            final TypeMirror rootType) {

        final var pathSegments = findPathFromRoot(graph, sourceRoot, targetLeaf);
        final List<ReadAccessor> accessors = new ArrayList<>();
        var currentType = rootType;
        var segmentIndex = 0;
        final var fullChain = buildFullChainString(pathSegments);

        for (final var node : pathSegments) {
            final var availableProps = discoverSourcePropertyMap(currentType);
            final var accessor = availableProps.get(node.getName());

            if (accessor == null) {
                return AccessorChainResult.failed(new AccessResolutionFailure(
                        node.getName(), segmentIndex, fullChain, currentType, Set.copyOf(availableProps.keySet())));
            }

            accessors.add(accessor);
            currentType = accessor.getType();
            segmentIndex++;
        }

        return AccessorChainResult.success(List.copyOf(accessors), currentType);
    }

    private static List<SourcePropertyNode> findPathFromRoot(
            final DefaultDirectedGraph<Object, Object> graph,
            final SourceRootNode sourceRoot,
            final SourcePropertyNode targetLeaf) {
        final List<SourcePropertyNode> result = new ArrayList<>();
        buildPath(graph, sourceRoot, targetLeaf, new ArrayList<>(), result);
        return result;
    }

    private static boolean buildPath(
            final DefaultDirectedGraph<Object, Object> graph,
            final Object current,
            final SourcePropertyNode target,
            final List<SourcePropertyNode> currentPath,
            final List<SourcePropertyNode> result) {
        for (final var edge : graph.outgoingEdgesOf(current)) {
            if (!(edge instanceof AccessEdge)) {
                continue;
            }
            final var next = graph.getEdgeTarget(edge);
            if (!(next instanceof SourcePropertyNode)) {
                continue;
            }
            final var nextNode = (SourcePropertyNode) next;
            currentPath.add(nextNode);

            if (nextNode.equals(target)) {
                result.addAll(currentPath);
                currentPath.remove(currentPath.size() - 1);
                return true;
            }

            if (buildPath(graph, nextNode, target, currentPath, result)) {
                currentPath.remove(currentPath.size() - 1);
                return true;
            }
            currentPath.remove(currentPath.size() - 1);
        }
        return false;
    }

    private static String buildFullChainString(final List<SourcePropertyNode> path) {
        final var sb = new StringBuilder();
        for (final var node : path) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(node.getName());
        }
        return sb.toString();
    }

    private static String buildChainPath(
            final DefaultDirectedGraph<Object, Object> graph,
            final SourceRootNode sourceRoot,
            final SourcePropertyNode targetLeaf) {
        final var path = new ArrayList<SourcePropertyNode>();
        buildPath(graph, sourceRoot, targetLeaf, new ArrayList<>(), path);
        return buildFullChainString(path);
    }

    private Map<String, ReadAccessor> discoverSourcePropertyMap(final TypeMirror type) {
        final Map<String, ReadAccessor> merged = new LinkedHashMap<>();
        final Map<String, Integer> priorities = new LinkedHashMap<>();

        for (final SourcePropertyDiscovery strategy : sourceDiscoveries) {
            for (final ReadAccessor accessor : strategy.discover(type, elements, types)) {
                final int current = priorities.getOrDefault(accessor.getName(), Integer.MIN_VALUE);
                if (strategy.priority() > current) {
                    merged.put(accessor.getName(), accessor);
                    priorities.put(accessor.getName(), strategy.priority());
                }
            }
        }
        return merged;
    }

    private Map<String, WriteAccessor> discoverTargetProperties(final TypeMirror type) {
        final Map<String, WriteAccessor> merged = new LinkedHashMap<>();
        final Map<String, Integer> priorities = new LinkedHashMap<>();

        for (final TargetPropertyDiscovery strategy : targetDiscoveries) {
            for (final WriteAccessor accessor : strategy.discover(type, elements, types)) {
                final int current = priorities.getOrDefault(accessor.getName(), Integer.MIN_VALUE);
                if (strategy.priority() > current) {
                    merged.put(accessor.getName(), accessor);
                    priorities.put(accessor.getName(), strategy.priority());
                }
            }
        }
        return merged;
    }

    @Nullable
    private static SourceRootNode findSourceRoot(final DefaultDirectedGraph<Object, Object> graph) {
        return graph.vertexSet().stream()
                .filter(v -> v instanceof SourceRootNode)
                .map(v -> (SourceRootNode) v)
                .findFirst()
                .orElse(null);
    }

    private TransformResolution resolveTransformPath(
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

                        if (!graph.containsEdge(inputNode, outputNode) && canAddEdge(prop, ctx)) {
                            graph.addEdge(inputNode, outputNode, new TransformEdge(strategy, prop));
                            expanded = true;
                        }
                    }
                }
            }

            final var pathResult = new BFSShortestPath<>(graph).getPath(sourceNode, targetNode);
            if (pathResult != null) {
                resolvePathTemplates(pathResult, ctx);
                return new TransformResolution(graph, pathResult);
            }

            if (!expanded) {
                break;
            }
        }
        return new TransformResolution(graph, null);
    }

    private void resolvePathTemplates(final GraphPath<TypeNode, TransformEdge> path, final ResolutionContext ctx) {
        for (final var edge : path.getEdgeList()) {
            edge.resolveTemplate(resolveCodeTemplate(edge.getProposal(), ctx));
        }
    }

    private boolean canAddEdge(final TransformProposal prop, final ResolutionContext ctx) {
        final var constraint = prop.getElementConstraint();
        if (constraint == null) {
            return true;
        }
        return resolveTransformPath(constraint.getFromType(), constraint.getToType(), ctx).getPath() != null;
    }

    private CodeTemplate resolveCodeTemplate(final TransformProposal proposal, final ResolutionContext ctx) {
        if (proposal.getElementConstraint() == null || proposal.getTemplateComposer() == null) {
            return proposal.getCodeTemplate();
        }
        final var constraint = proposal.getElementConstraint();
        final var innerPath =
                resolveTransformPath(constraint.getFromType(), constraint.getToType(), ctx).getPath();

        if (innerPath == null) {
            return proposal.getCodeTemplate();
        }
        final CodeTemplate innerTemplate = composeTemplates(innerPath.getEdgeList());
        return proposal.getTemplateComposer().apply(innerTemplate);
    }

    @SuppressWarnings("NullAway") // codeTemplate is set by resolvePathTemplates before edges are composed
    private static CodeTemplate composeTemplates(final List<TransformEdge> edges) {
        return input -> {
            var result = input;
            for (final var edge : edges) {
                result = edge.getCodeTemplate().apply(result);
            }
            return result;
        };
    }

    private static TypeNode getOrCreateNode(
            final DefaultDirectedGraph<TypeNode, TransformEdge> graph,
            final Map<String, TypeNode> nodeIndex,
            final TypeMirror type) {
        final var key = type.toString();
        return nodeIndex.computeIfAbsent(key, k -> {
            final var node = new TypeNode(type, key);
            graph.addVertex(node);
            return node;
        });
    }

    private static <T> List<T> loadServices(final Class<T> serviceClass) {
        final var loader = ServiceLoader.load(serviceClass, serviceClass.getClassLoader());
        final List<T> result = new ArrayList<>();
        loader.forEach(result::add);
        return List.copyOf(result);
    }

    private static <T> List<T> loadAndSortByPriority(final Class<T> serviceClass, final ToIntFunction<T> priorityFn) {
        return ServiceLoader.load(serviceClass, serviceClass.getClassLoader()).stream()
                .map(ServiceLoader.Provider::get)
                .sorted(comparingInt((T s) -> -priorityFn.applyAsInt(s)))
                .collect(toUnmodifiableList());
    }

    private static final class AccessorChainResult {
        final List<ReadAccessor> accessors;

        @Nullable
        final TypeMirror resolvedType;

        @Nullable
        final AccessResolutionFailure failure;

        private AccessorChainResult(
                final List<ReadAccessor> accessors,
                @Nullable final TypeMirror resolvedType,
                @Nullable final AccessResolutionFailure failure) {
            this.accessors = accessors;
            this.resolvedType = resolvedType;
            this.failure = failure;
        }

        static AccessorChainResult success(final List<ReadAccessor> accessors, final TypeMirror type) {
            return new AccessorChainResult(accessors, type, null);
        }

        static AccessorChainResult failed(final AccessResolutionFailure failure) {
            return new AccessorChainResult(List.of(), null, failure);
        }
    }
}
