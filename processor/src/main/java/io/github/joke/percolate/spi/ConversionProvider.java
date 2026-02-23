package io.github.joke.percolate.spi;

import io.github.joke.percolate.graph.edge.ConversionEdge;

import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;

/**
 * SPI for discovering type conversions. Each provider returns all conversions
 * possible from a given source type. Used by LazyMappingGraph during traversal.
 */
public interface ConversionProvider {

    /**
     * Returns all types this source can convert to, along with the edge to add to the graph.
     * Called lazily during graph traversal — results are cached per source node.
     */
    List<Conversion> possibleConversions(TypeMirror source, ProcessingEnvironment env);

    final class Conversion {
        private final TypeMirror targetType;
        private final ConversionEdge edge;

        public Conversion(TypeMirror targetType, ConversionEdge edge) {
            this.targetType = targetType;
            this.edge = edge;
        }

        public TypeMirror getTargetType() {
            return targetType;
        }

        public ConversionEdge getEdge() {
            return edge;
        }
    }
}
