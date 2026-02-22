package io.github.joke.caffeinate.resolution;

import io.github.joke.caffeinate.analysis.property.Property;
import javax.lang.model.type.TypeMirror;

/** A single fully-resolved property mapping: a source expression -> a target property. */
public final class ResolvedMapAnnotation {
    private final Property targetProperty;
    private final String sourceExpression;   // e.g. "order.getVenue()"
    private final TypeMirror sourceType;     // resolved type of sourceExpression

    public ResolvedMapAnnotation(Property targetProperty, String sourceExpression, TypeMirror sourceType) {
        this.targetProperty = targetProperty;
        this.sourceExpression = sourceExpression;
        this.sourceType = sourceType;
    }

    public Property getTargetProperty() { return targetProperty; }
    public String getSourceExpression() { return sourceExpression; }
    public TypeMirror getSourceType() { return sourceType; }
}
