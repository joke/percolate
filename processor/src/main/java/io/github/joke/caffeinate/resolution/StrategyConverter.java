package io.github.joke.caffeinate.resolution;

import io.github.joke.caffeinate.codegen.strategy.TypeMappingStrategy;
import javax.lang.model.type.TypeMirror;

/** A converter backed by a built-in TypeMappingStrategy (e.g. Optional-wrapping). */
public final class StrategyConverter implements Converter {
    private final TypeMappingStrategy strategy;
    private final TypeMirror sourceType;
    private final TypeMirror targetType;

    public StrategyConverter(TypeMappingStrategy strategy, TypeMirror sourceType, TypeMirror targetType) {
        this.strategy = strategy;
        this.sourceType = sourceType;
        this.targetType = targetType;
    }

    public TypeMappingStrategy getStrategy() {
        return strategy;
    }

    public TypeMirror getSourceType() {
        return sourceType;
    }

    public TypeMirror getTargetType() {
        return targetType;
    }
}
