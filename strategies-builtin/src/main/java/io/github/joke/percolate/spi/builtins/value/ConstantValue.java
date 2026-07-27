package io.github.joke.percolate.spi.builtins.value;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.DirectiveInput;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.LiteralCoercion;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.Offer;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * Produces a {@code @Map(constant = "...")} target value: on a demand whose directive declares a present
 * {@code "constant"} input, it coerces the raw literal to the demanded type via {@link LiteralCoercion} and, on
 * success, emits a single zero-port {@link OperationSpec} (legitimately vacuously SAT) whose {@link
 * OperationCodegen} renders the coerced literal, producing a {@code NON_NULL} Value, stamping the {@code
 * "constant"} input consumed. It emits nothing when no {@code constant} is present (not mine); when the constant
 * cannot be coerced it refuses (design D1 of change {@code decouple-engine-from-strategy-semantics}), carrying the
 * {@code "constant"} input's own {@link io.github.joke.percolate.spi.Subject} so the deepest-miss renderer can
 * position the message at the offending literal. It is myopic: it reads only the demand, never the graph.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class ConstantValue implements ExpansionStrategy {

    static final String CONSTANT_KEY = "constant";

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var target = demand.targetType();
        return demand.directive()
                .flatMap(directive -> directive.input(CONSTANT_KEY))
                .flatMap(input -> input.getValue().map(raw -> offerFor(input, raw, target)))
                .map(Stream::of)
                .orElseGet(Stream::empty);
    }

    static Offer offerFor(final DirectiveInput input, final String raw, final TypeMirror target) {
        return LiteralCoercion.coerce(raw, target)
                .<Offer>map(literal -> Offer.of(constantSpec(target, literal, input)))
                .orElseGet(
                        () -> Offer.refusal(input.getSubject(), "cannot coerce '" + raw + "' to " + typeName(target)));
    }

    static OperationSpec constantSpec(final TypeMirror target, final CodeBlock literal, final DirectiveInput input) {
        final OperationCodegen codegen = inputs -> literal;
        return OperationSpec.of(literal.toString(), codegen, Weights.STEP, List.of(), target, Nullability.NON_NULL)
                .withConsumed(Set.of(input));
    }

    static String typeName(final TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return type.toString();
        }
        final var element = ((DeclaredType) type).asElement();
        return element instanceof TypeElement
                ? ((TypeElement) element).getSimpleName().toString()
                : type.toString();
    }
}
