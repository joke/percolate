package io.github.joke.percolate.stage;

import java.util.Objects;

/** Key for MethodRegistry lookups. Uses canonical type name strings to avoid TypeMirror equality issues. */
public final class TypePair {
    private final String inTypeName;
    private final String outTypeName;

    public TypePair(String inTypeName, String outTypeName) {
        this.inTypeName = inTypeName;
        this.outTypeName = outTypeName;
    }

    public String getInTypeName() {
        return inTypeName;
    }

    public String getOutTypeName() {
        return outTypeName;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TypePair)) {
            return false;
        }
        TypePair other = (TypePair) o;
        return inTypeName.equals(other.inTypeName) && outTypeName.equals(other.outTypeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inTypeName, outTypeName);
    }

    @Override
    public String toString() {
        return inTypeName + " -> " + outTypeName;
    }
}
