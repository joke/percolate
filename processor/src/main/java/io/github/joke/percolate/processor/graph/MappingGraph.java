package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.processor.model.DiscoveredMethod;
import java.util.List;
import javax.lang.model.element.TypeElement;
import lombok.Value;
import org.jgrapht.graph.DefaultDirectedGraph;

@Value
public class MappingGraph {
    TypeElement mapperType;
    List<DiscoveredMethod> methods;
    DefaultDirectedGraph<PropertyNode, MappingEdge> graph;
}
