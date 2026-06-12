package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.ExpansionStep;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Frontier;
import io.github.joke.percolate.spi.LiteralCoercion;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * Produces a {@code @Map(constant = "...")} target value: on a frontier whose {@link Directive} declares a present
 * {@code constant}, it coerces the raw literal to the frontier's target type via {@link LiteralCoercion} and, on
 * success, emits a single zero-input {@link io.github.joke.percolate.spi.Intent#BOUNDARY} terminal producer whose
 * {@link EdgeCodegen} renders the coerced literal (ignoring its empty inputs). It emits nothing when no
 * {@code constant} is present, and nothing when the value cannot be coerced — leaving the demand UNSAT for the late
 * coercion-failure diagnostic to report. It is myopic: it reads only the frontier, never the graph.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class ConstantValue implements ExpansionStrategy {

    @Override
    public Stream<ExpansionStep> expand(final Frontier frontier, final ResolveCtx ctx) {
        return frontier.directive()
                .flatMap(Directive::constant)
                .flatMap(raw -> LiteralCoercion.coerce(raw, frontier.targetType()))
                .map(literal -> boundaryStep(frontier.targetType(), literal))
                .map(Stream::of)
                .orElseGet(Stream::empty);
    }

    private static ExpansionStep boundaryStep(final TypeMirror target, final CodeBlock literal) {
        final EdgeCodegen codegen = (vars, inputs) -> literal;
        return ExpansionStep.boundary(List.of(), target, codegen, Weights.STEP);
    }
}
