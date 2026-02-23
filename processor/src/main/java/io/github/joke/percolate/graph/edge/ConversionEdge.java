package io.github.joke.percolate.graph.edge;

import javax.lang.model.type.TypeMirror;

/**
 * Edge representing a type conversion between two TypeNodes or PropertyNode to TypeNode.
 * Carries enough information for CodeGenStage to emit the correct expression.
 */
public final class ConversionEdge implements GraphEdge {

    private final Kind kind;
    private final TypeMirror sourceType;
    private final TypeMirror targetType;
    private final String expressionTemplate;

    public ConversionEdge(Kind kind, TypeMirror sourceType, TypeMirror targetType, String expressionTemplate) {
        this.kind = kind;
        this.sourceType = sourceType;
        this.targetType = targetType;
        this.expressionTemplate = expressionTemplate;
    }

    public Kind getKind() {
        return kind;
    }

    public TypeMirror getSourceType() {
        return sourceType;
    }

    public TypeMirror getTargetType() {
        return targetType;
    }

    public String getExpressionTemplate() {
        return expressionTemplate;
    }

    @Override
    public String toString() {
        return "Conversion{" + kind + ": " + sourceType + " -> " + targetType + "}";
    }

    public enum Kind {
        MAPPER_METHOD,
        OPTIONAL_WRAP,
        OPTIONAL_UNWRAP,
        LIST_MAP,
        PRIMITIVE_WIDEN,
        PRIMITIVE_BOX,
        PRIMITIVE_UNBOX,
        ENUM_VALUE_OF,
        SUBTYPE
    }
}
