package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Demand;
import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.LiteralCoercion;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * Produces a {@code @Map(constant = "...")} target value: on a demand whose {@link Directive} declares a present
 * {@code constant}, it coerces the raw literal to the demanded type via {@link LiteralCoercion} and, on success,
 * emits a single zero-port {@link OperationSpec} (legitimately vacuously SAT) whose {@link OperationCodegen}
 * renders the coerced literal, producing a {@code NON_NULL} Value. It emits nothing when no {@code constant} is
 * present, and nothing when the value cannot be coerced — leaving the demand UNSAT for the late coercion-failure
 * diagnostic to report. It is myopic: it reads only the demand, never the graph.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class ConstantValue implements ExpansionStrategy {

    @Override
    public Stream<OperationSpec> expand(final Demand demand, final ResolveCtx ctx) {
        return demand.directive()
                .flatMap(Directive::constant)
                .flatMap(raw -> LiteralCoercion.coerce(raw, demand.targetType()))
                .map(literal -> constantSpec(demand.targetType(), literal))
                .map(Stream::of)
                .orElseGet(Stream::empty);
    }

    private static OperationSpec constantSpec(final TypeMirror target, final CodeBlock literal) {
        final OperationCodegen codegen = inputs -> literal;
        return OperationSpec.of(literal.toString(), codegen, Weights.STEP, List.of(), target, Nullability.NON_NULL);
    }
}
