package io.github.joke.percolate.spi.builtins.memberconflict;

import io.github.joke.percolate.lib.javapoet.ClassName;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.spi.DirectiveInput;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.MemberRequest;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Test-only fixture, registered via {@code META-INF/services} for the {@code strategies-builtin} test classpath
 * only: proves {@code MemberPlan}'s class-member-agreement guard (design D11 of change
 * {@code decouple-engine-from-strategy-semantics}) by deliberately <b>not</b> discriminating its dedup key by the
 * {@code @Map(format = "…")} value it renders into the member initializer — the exact strategy bug the guard exists
 * to catch. Applies only to {@code examples.memberconflict.Widget}, a fixture type unique to this test, so it is
 * inert for every other test on the same classpath.
 */
public final class MemberConflictFixtureStrategy implements ExpansionStrategy {

    private static final String WIDGET = "examples.memberconflict.Widget";
    private static final String STRING = "java.lang.String";
    private static final ClassName STRING_TYPE = ClassName.get("java.lang", "String");
    private static final ClassName WIDGET_TYPE = ClassName.get("examples.memberconflict", "Widget");

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        if (!ctx.isType(demand.targetType(), WIDGET)) {
            return Stream.empty();
        }
        final var stringElement = ctx.typeElementNamed(STRING);
        if (stringElement == null) {
            return Stream.empty();
        }
        final var formatInput = demand.directive().flatMap(d -> d.input("format"));
        final var tag = formatInput.flatMap(DirectiveInput::getValue).orElse("default");
        final var request = new MemberRequest(STRING_TYPE, CodeBlock.of("$S", tag), "widget-member");
        final var port = new Port("value", stringElement.asType(), Nullability.NON_NULL);
        final OperationCodegen codegen =
                inputs -> CodeBlock.of("new $T($L, $L)", WIDGET_TYPE, inputs.single(), inputs.member("widget-member"));
        final var consumed = formatInput.map(Set::of).orElseGet(Set::of);
        return Stream.of(OperationSpec.of(
                        "widget-from-" + tag,
                        codegen,
                        Weights.STEP,
                        List.of(port),
                        demand.targetType(),
                        Nullability.NON_NULL)
                .withConsumed(consumed)
                .withMemberRequests(List.of(request)));
    }
}
