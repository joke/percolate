package io.github.joke.percolate.processor.spi;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.transform.TransformProposal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

@AutoService(TypeTransformStrategy.class)
public final class MethodCallStrategy implements TypeTransformStrategy {

    @Override
    public Optional<TransformProposal> canProduce(
            final TypeMirror sourceType, final TypeMirror targetType, final ResolutionContext ctx) {
        final var types = ctx.getTypes();
        final var using = ctx.getUsing();

        // 3.1 Name filtering: when using is non-empty, filter to methods with matching name
        // 3.2 Type-compatible candidates
        final List<ExecutableElement> candidates = ctx.getElements().getAllMembers(ctx.getMapperType()).stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .map(e -> (ExecutableElement) e)
                .filter(m -> m.getParameters().size() == 1)
                .filter(m -> m.getReturnType().getKind() != TypeKind.VOID)
                .filter(m -> !Objects.equals(m, ctx.getCurrentMethod()))
                .filter(m -> using.isEmpty() || m.getSimpleName().toString().equals(using))
                .filter(m -> types.isAssignable(sourceType, m.getParameters().get(0).asType()))
                .filter(m -> types.isAssignable(m.getReturnType(), targetType))
                .collect(toUnmodifiableList());

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // 3.2 Most-specific-match by parameter type (narrowest param wins)
        final List<ExecutableElement> bestByParam = findMostSpecificByParam(candidates, types);

        // 3.3 Return type tiebreaker when param specificity is equal
        final List<ExecutableElement> best = bestByParam.size() > 1
                ? findMostSpecificByReturn(bestByParam, types)
                : bestByParam;

        // 3.4 Single winner found
        if (best.size() == 1) {
            final var m = best.get(0);
            return Optional.of(new TransformProposal(
                    sourceType, targetType, input -> CodeBlock.of("$L($L)", m.getSimpleName(), input), this));
        }

        // 3.4 Ambiguous (incomparable param types) — return empty; ambiguity detected by ResolveTransformsStage
        return Optional.empty();
    }

    /**
     * Returns the subset of {@code candidates} whose parameter type is not strictly dominated by
     * any other candidate. Candidate A dominates B when A's param is assignable to B's param but
     * not vice-versa (A is more specific).
     */
    private static List<ExecutableElement> findMostSpecificByParam(
            final List<ExecutableElement> candidates, final Types types) {
        return candidates.stream()
                .filter(a -> candidates.stream().noneMatch(b ->
                        b != a
                        && types.isAssignable(
                                b.getParameters().get(0).asType(), a.getParameters().get(0).asType())
                        && !types.isAssignable(
                                a.getParameters().get(0).asType(), b.getParameters().get(0).asType())))
                .collect(toUnmodifiableList());
    }

    /**
     * Returns the subset of {@code candidates} whose return type is not strictly dominated by any
     * other candidate. Used as a tiebreaker when param specificity is equal.
     */
    private static List<ExecutableElement> findMostSpecificByReturn(
            final List<ExecutableElement> candidates, final Types types) {
        return candidates.stream()
                .filter(a -> candidates.stream().noneMatch(b ->
                        b != a
                        && types.isAssignable(b.getReturnType(), a.getReturnType())
                        && !types.isAssignable(a.getReturnType(), b.getReturnType())))
                .collect(toUnmodifiableList());
    }
}
