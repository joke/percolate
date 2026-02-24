package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.graph.GraphRenderer;
import io.github.joke.percolate.graph.LazyMappingGraph;
import io.github.joke.percolate.graph.edge.ConstructorParamEdge;
import io.github.joke.percolate.graph.edge.GraphEdge;
import io.github.joke.percolate.graph.node.ConstructorNode;
import io.github.joke.percolate.graph.node.GraphNode;
import io.github.joke.percolate.graph.node.PropertyNode;
import io.github.joke.percolate.graph.node.TypeNode;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.ConversionProvider;
import io.github.joke.percolate.spi.impl.EnumProvider;
import io.github.joke.percolate.spi.impl.ListProvider;
import io.github.joke.percolate.spi.impl.MapperMethodProvider;
import io.github.joke.percolate.spi.impl.OptionalProvider;
import io.github.joke.percolate.spi.impl.PrimitiveWideningProvider;
import io.github.joke.percolate.spi.impl.SubtypeProvider;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.jspecify.annotations.Nullable;

@RoundScoped
public class ValidateStage {

    private final Messager messager;
    private final ProcessingEnvironment processingEnv;
    private final Types types;

    @Inject
    ValidateStage(Messager messager, ProcessingEnvironment processingEnv) {
        this.messager = messager;
        this.processingEnv = processingEnv;
        this.types = processingEnv.getTypeUtils();
    }

    public ValidationResult execute(GraphResult graphResult) {
        DirectedWeightedMultigraph<GraphNode, GraphEdge> baseGraph = graphResult.getGraph();
        List<ConversionProvider> providers = buildProviders(graphResult);
        LazyMappingGraph lazyGraph = new LazyMappingGraph(baseGraph, providers, processingEnv, 5);

        boolean hasFatalErrors = false;

        // Phase 1: Cycle detection on base graph (before lazy expansion adds vertices)
        CycleDetector<GraphNode, GraphEdge> cycleDetector = new CycleDetector<>(baseGraph);
        if (cycleDetector.detectCycles()) {
            hasFatalErrors = true;
            Set<GraphNode> cycleNodes = cycleDetector.findCycles();
            String nodeNames = cycleNodes.stream().map(GraphNode::toString).collect(joining(", "));
            messager.printMessage(Diagnostic.Kind.ERROR, "Circular dependency detected involving: " + nodeNames);
        }

        // Phase 2: Constructor param validation
        Set<ConstructorNode> constructorNodes = lazyGraph.vertexSet().stream()
                .filter(ConstructorNode.class::isInstance)
                .map(ConstructorNode.class::cast)
                .collect(toSet());

        for (ConstructorNode constructorNode : constructorNodes) {
            Set<String> mappedParams = lazyGraph.incomingEdgesOf(constructorNode).stream()
                    .filter(ConstructorParamEdge.class::isInstance)
                    .map(ConstructorParamEdge.class::cast)
                    .map(ConstructorParamEdge::getParameterName)
                    .collect(toSet());

            List<Property> parameters = constructorNode.getDescriptor().getParameters();
            Set<String> missingParams = new LinkedHashSet<>();
            List<String> typeErrors = new ArrayList<>();

            for (Property param : parameters) {
                if (!mappedParams.contains(param.getName())) {
                    missingParams.add(param.getName());
                    continue;
                }

                // Type compatibility check for mapped params
                @Nullable
                ConstructorParamEdge paramEdge = lazyGraph.incomingEdgesOf(constructorNode).stream()
                        .filter(ConstructorParamEdge.class::isInstance)
                        .map(ConstructorParamEdge.class::cast)
                        .filter(e -> e.getParameterName().equals(param.getName()))
                        .findFirst()
                        .orElse(null);

                if (paramEdge != null) {
                    GraphNode sourceNode = lazyGraph.getEdgeSource(paramEdge);
                    @Nullable TypeMirror sourceType = getNodeType(sourceNode);
                    TypeMirror targetType = param.getType();

                    if (sourceType != null && !types.isAssignable(sourceType, targetType)) {
                        if (!hasConversion(lazyGraph, sourceNode, sourceType, targetType)) {
                            typeErrors.add(
                                    param.getName() + ": " + sourceType + " cannot be converted to " + targetType);
                        }
                    }
                }
            }

            if (!missingParams.isEmpty()) {
                hasFatalErrors = true;
                String rendered = GraphRenderer.renderConstructorNode(baseGraph, constructorNode, missingParams);
                String message = "Unmapped target properties in "
                        + constructorNode.getTargetType().getQualifiedName() + ": "
                        + String.join(", ", missingParams) + "\n" + rendered;
                messager.printMessage(Diagnostic.Kind.ERROR, message);
            }

            if (!typeErrors.isEmpty()) {
                hasFatalErrors = true;
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "Type mismatches in "
                                + constructorNode.getTargetType().getQualifiedName() + ": "
                                + String.join("; ", typeErrors));
            }
        }

        return new ValidationResult(graphResult, lazyGraph, hasFatalErrors);
    }

    /**
     * Checks whether a conversion path exists from sourceNode to the targetType,
     * either via direct lazy-expanded edges, enum-to-enum conversion, or through
     * multi-step conversion chains (e.g., mapper method + optional wrap).
     */
    private boolean hasConversion(
            LazyMappingGraph lazyGraph, GraphNode sourceNode, TypeMirror sourceType, TypeMirror targetType) {
        // Check enum-to-enum conversion
        if (EnumProvider.canConvertEnums(sourceType, targetType)) {
            return true;
        }

        // Check single-step lazy conversions
        Set<GraphEdge> outgoing = lazyGraph.outgoingEdgesOf(sourceNode);
        for (GraphEdge edge : outgoing) {
            GraphNode target = lazyGraph.getEdgeTarget(edge);
            @Nullable TypeMirror convertedType = getNodeType(target);
            if (convertedType != null && types.isAssignable(convertedType, targetType)) {
                return true;
            }
        }

        // Check two-step conversions (e.g., mapper method -> optional wrap)
        for (GraphEdge edge : outgoing) {
            GraphNode intermediate = lazyGraph.getEdgeTarget(edge);
            @Nullable TypeMirror intermediateType = getNodeType(intermediate);
            if (intermediateType == null) {
                continue;
            }
            // Check enum conversion from intermediate
            if (EnumProvider.canConvertEnums(intermediateType, targetType)) {
                return true;
            }
            Set<GraphEdge> secondStep = lazyGraph.outgoingEdgesOf(intermediate);
            for (GraphEdge edge2 : secondStep) {
                GraphNode target2 = lazyGraph.getEdgeTarget(edge2);
                @Nullable TypeMirror finalType = getNodeType(target2);
                if (finalType != null && types.isAssignable(finalType, targetType)) {
                    return true;
                }
            }
        }

        return false;
    }

    private List<ConversionProvider> buildProviders(GraphResult graphResult) {
        List<ConversionProvider> providers = new ArrayList<>();
        providers.add(new MapperMethodProvider(graphResult.getMappers()));
        providers.add(new ListProvider(graphResult.getMappers()));
        providers.add(new OptionalProvider());
        providers.add(new PrimitiveWideningProvider());
        providers.add(new SubtypeProvider());
        providers.add(new EnumProvider());
        return providers;
    }

    private static @Nullable TypeMirror getNodeType(GraphNode node) {
        if (node instanceof TypeNode) {
            return ((TypeNode) node).getType();
        }
        if (node instanceof PropertyNode) {
            return ((PropertyNode) node).getProperty().getType();
        }
        return null;
    }
}
