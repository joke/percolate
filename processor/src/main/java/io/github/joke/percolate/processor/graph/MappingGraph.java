package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.processor.model.MappingMethodModel;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.TypeElement;
import lombok.Value;
import org.jgrapht.graph.DefaultDirectedGraph;

@Value
public class MappingGraph {
    TypeElement mapperType;
    List<MappingMethodModel> methods;
    Map<MappingMethodModel, DefaultDirectedGraph<Object, Object>> methodGraphs;
}
