package io.github.joke.percolate.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

@Value
public final class BridgeStep {
    TypeMirror inputType;
    TypeMirror outputType;
    int weight;
    EdgeCodegen codegen;
    List<ElementSeed> elementSeeds;

    public BridgeStep(
            final TypeMirror inputType,
            final TypeMirror outputType,
            final int weight,
            final EdgeCodegen codegen,
            final List<ElementSeed> elementSeeds) {
        this.inputType = inputType;
        this.outputType = outputType;
        this.weight = weight;
        this.codegen = codegen;
        this.elementSeeds = Collections.unmodifiableList(new ArrayList<>(elementSeeds));
    }
}
