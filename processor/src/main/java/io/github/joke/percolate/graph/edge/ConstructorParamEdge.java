package io.github.joke.percolate.graph.edge;

/** Edge from a PropertyNode or TypeNode to a ConstructorNode — feeding a value into a constructor parameter. */
public final class ConstructorParamEdge implements GraphEdge {

    private final String parameterName;
    private final int parameterIndex;

    public ConstructorParamEdge(String parameterName, int parameterIndex) {
        this.parameterName = parameterName;
        this.parameterIndex = parameterIndex;
    }

    public String getParameterName() {
        return parameterName;
    }

    public int getParameterIndex() {
        return parameterIndex;
    }

    @Override
    public String toString() {
        return "ConstructorParam{" + parameterName + "[" + parameterIndex + "]}";
    }
}
