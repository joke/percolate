package io.github.joke.percolate.docs.extending;

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
import javax.lang.model.type.TypeMirror;

// tag::strategy[]
// The strategy side reads only the "literal" key LiteralDirectiveReader published — exactly how the
// built-in ConstantValue reads "constant". It never learns @Literal exists.
public final class LiteralValue implements ExpansionStrategy {

    static final String LITERAL_KEY = "literal";

    @Override
    public Stream<Offer> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var target = demand.targetType();
        return demand.directive()
                .flatMap(directive -> directive.input(LITERAL_KEY))
                .flatMap(input -> input.getValue().map(raw -> offerFor(input, raw, target)))
                .map(Stream::of)
                .orElseGet(Stream::empty);
    }

    static Offer offerFor(final DirectiveInput input, final String raw, final TypeMirror target) {
        return LiteralCoercion.coerce(raw, target)
                .<Offer>map(literal -> Offer.of(literalSpec(target, literal, input)))
                .orElseGet(() -> Offer.refusal(input.getSubject(), "cannot coerce '" + raw + "' to " + target));
    }

    static OperationSpec literalSpec(final TypeMirror target, final CodeBlock literal, final DirectiveInput input) {
        final OperationCodegen codegen = inputs -> literal;
        return OperationSpec.of(literal.toString(), codegen, Weights.STEP, List.of(), target, Nullability.NON_NULL)
                .withConsumed(Set.of(input));
    }
}
// end::strategy[]
