package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.DirectiveInput;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.LiteralCoercion;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * Produces a {@code @Map(constant = "...")} target value: on a demand whose directive declares a present
 * {@code "constant"} input, it coerces the raw literal to the demanded type via {@link LiteralCoercion} and, on
 * success, emits a single zero-port {@link OperationSpec} (legitimately vacuously SAT) whose {@link
 * OperationCodegen} renders the coerced literal, producing a {@code NON_NULL} Value, stamping the {@code
 * "constant"} input consumed. It emits nothing when no {@code constant} is present, and nothing when the value
 * cannot be coerced — leaving the demand UNSAT for the late coercion-failure diagnostic to report. It is myopic: it
 * reads only the demand, never the graph.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class ConstantValue implements ExpansionStrategy {

    static final String CONSTANT_KEY = "constant";

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        return demand.directive()
                .flatMap(directive -> directive.input(CONSTANT_KEY))
                .flatMap(input -> input.getValue()
                        .flatMap(raw -> LiteralCoercion.coerce(raw, demand.targetType()))
                        .map(literal -> constantSpec(demand.targetType(), literal, input)))
                .map(Stream::of)
                .orElseGet(Stream::empty);
    }

    static OperationSpec constantSpec(final TypeMirror target, final CodeBlock literal, final DirectiveInput input) {
        final OperationCodegen codegen = inputs -> literal;
        return OperationSpec.of(literal.toString(), codegen, Weights.STEP, List.of(), target, Nullability.NON_NULL)
                .withConsumed(Set.of(input));
    }
}
