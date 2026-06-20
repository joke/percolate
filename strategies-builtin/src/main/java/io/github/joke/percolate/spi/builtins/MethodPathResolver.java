package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Demand;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * Resolves one source-path segment to a no-arg accessor method whose name equals the segment (a fluent accessor,
 * e.g. {@code value()}) on the demanded (parent) type, emitting a one-port {@link OperationSpec} typed to the
 * method's return type. It is candidate-free: the driver pins the parent type as {@link Demand#targetType()}. The
 * operation renders {@code parent.value()}; the produced value's nullness is the method's, resolved through the
 * demand oracle.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class MethodPathResolver implements ExpansionStrategy {

    @Override
    public Stream<OperationSpec> expand(final Demand demand, final ResolveCtx ctx) {
        return Segments.single(demand)
                .map(segment -> resolve(demand.targetType(), segment, demand, ctx))
                .orElseGet(Stream::empty);
    }

    private Stream<OperationSpec> resolve(
            final TypeMirror parentType, final String segment, final Demand demand, final ResolveCtx ctx) {
        final var typeElement = Members.asTypeElement(parentType, ctx);
        if (typeElement.isEmpty()) {
            return Stream.empty();
        }
        for (final var member : Members.declaredMembersOf(typeElement.get(), ctx)) {
            final var match = matchAccessor(member, segment);
            if (match.isPresent()) {
                return Stream.of(buildSpec(match.get(), segment, parentType, demand));
            }
        }
        return Stream.empty();
    }

    private Optional<ExecutableElement> matchAccessor(final Element candidate, final String segment) {
        if (candidate.getKind() != ElementKind.METHOD) {
            return Optional.empty();
        }
        final var method = (ExecutableElement) candidate;
        if (Members.isInObjectClass(method) || !method.getParameters().isEmpty()) {
            return Optional.empty();
        }
        return method.getSimpleName().contentEquals(segment) ? Optional.of(method) : Optional.empty();
    }

    private OperationSpec buildSpec(
            final ExecutableElement method, final String segment, final TypeMirror parentType, final Demand demand) {
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L.$N()", inputs.single(), segment);
        final var port = new Port("value", parentType, Nullability.NON_NULL);
        final var returnType = method.getReturnType();
        final var outputNullness = demand.nullnessOf(returnType, method);
        return OperationSpec.of(
                segment + "()", codegen, Weights.STEP_METHOD, List.of(port), returnType, outputNullness);
    }
}
