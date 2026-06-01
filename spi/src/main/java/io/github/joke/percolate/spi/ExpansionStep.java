package io.github.joke.percolate.spi;

import java.util.List;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

/**
 * One step an {@link ExpansionStrategy} can emit: how the value at a frontier can be produced. A step has
 * {@code 0..N} input {@link Slot}s, an {@code output} type, a {@link Codegen} that assembles the inputs into the
 * output, an {@link Intent}, an optional {@link ElementScope} (container boundaries only), and a {@code weight}.
 *
 * <p>A {@link Intent#CONVERSION} step folds in place and has exactly one input (an in-place re-typing of the value
 * at the frontier's position). A {@link Intent#BOUNDARY} step opens a sub-group whose slots are its inputs:
 * {@code 0} for a terminal producer (e.g. a default value), {@code 1} for a getter or unary call, {@code N} for a
 * multi-arg assembly. {@code scope} is present only on container boundary steps.
 *
 * <p>Construct via the {@link #conversion}, {@link #boundary}, and {@link #containerBoundary} factories.
 */
@Value
public class ExpansionStep {

    List<Slot> inputs;
    TypeMirror output;
    Codegen codegen;
    Intent intent;
    Optional<ElementScope> scope;
    int weight;

    private ExpansionStep(
            final List<Slot> inputs,
            final TypeMirror output,
            final Codegen codegen,
            final Intent intent,
            final Optional<ElementScope> scope,
            final int weight) {
        if (intent == Intent.CONVERSION && inputs.size() != 1) {
            throw new IllegalArgumentException("CONVERSION step requires exactly one input, got " + inputs.size());
        }
        this.inputs = List.copyOf(inputs);
        this.output = output;
        this.codegen = codegen;
        this.intent = intent;
        this.scope = scope;
        this.weight = weight;
    }

    /** An in-place re-typing of the frontier's value (one input, no element-scope crossing). */
    public static ExpansionStep conversion(
            final Slot input, final TypeMirror output, final Codegen codegen, final int weight) {
        return new ExpansionStep(List.of(input), output, codegen, Intent.CONVERSION, Optional.empty(), weight);
    }

    /** A flow-identity boundary opening a sub-group with {@code 0..N} slots and no element-scope crossing. */
    public static ExpansionStep boundary(
            final List<Slot> inputs, final TypeMirror output, final Codegen codegen, final int weight) {
        return new ExpansionStep(inputs, output, codegen, Intent.BOUNDARY, Optional.empty(), weight);
    }

    /** A container boundary opening a one-slot sub-group that crosses element scope. */
    public static ExpansionStep containerBoundary(
            final Slot input,
            final TypeMirror output,
            final Codegen codegen,
            final ElementScope scope,
            final int weight) {
        return new ExpansionStep(List.of(input), output, codegen, Intent.BOUNDARY, Optional.of(scope), weight);
    }
}
