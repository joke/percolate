package io.github.joke.percolate.spi;

import lombok.Value;

import javax.lang.model.type.TypeMirror;

/**
 * One hop a {@link Bridge} can emit. The driver consumes a {@code BridgeStep} per emission and decides candidacy,
 * input-node allocation, and the {@code Location} of the freshly allocated input node by inspecting
 * {@link #scopeTransition} and {@link #elementRole}. See {@link ScopeTransition} for the three cases.
 */
@Value
public class BridgeStep {
    TypeMirror inputType;
    TypeMirror outputType;
    int weight;
    EdgeCodegen codegen;
    /** How this step relates to element scope. Defaults to {@link ScopeTransition#PRESERVING}. */
    ScopeTransition scopeTransition;
    /** Role name for the element scope this step participates in. Consulted only when {@code scopeTransition != PRESERVING}. */
    String elementRole;

    public BridgeStep(
            final TypeMirror inputType,
            final TypeMirror outputType,
            final int weight,
            final EdgeCodegen codegen,
            final ScopeTransition scopeTransition,
            final String elementRole) {
        this.inputType = inputType;
        this.outputType = outputType;
        this.weight = weight;
        this.codegen = codegen;
        this.scopeTransition = scopeTransition;
        this.elementRole = elementRole;
    }

    public BridgeStep(
            final TypeMirror inputType, final TypeMirror outputType, final int weight, final EdgeCodegen codegen) {
        this(inputType, outputType, weight, codegen, ScopeTransition.PRESERVING, "element");
    }
}
