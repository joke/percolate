package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.processor.internal.graph.AccessPath;
import io.github.joke.percolate.processor.internal.graph.Location;
import io.github.joke.percolate.processor.internal.graph.MethodScope;
import io.github.joke.percolate.processor.internal.graph.PortBinding;
import io.github.joke.percolate.processor.internal.graph.Scope;
import io.github.joke.percolate.processor.internal.graph.SourceLocation;
import io.github.joke.percolate.spi.OperationSpec;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;

/**
 * Bind-time self-call guard for the expansion driver: a method may not be landed calling its own method on its own
 * <b>whole parameter</b> ({@code this.m(param)}), which is always a degenerate self-call (runtime infinite recursion)
 * the cost model cannot see — over-emit + cost-prune cannot reject it because the whole-parameter binding is strictly
 * cheaper than any sub-part one. A self-call on a <b>sub-part</b> of the parameter (an {@code ACCESS} source such as
 * {@code src.getNext()}) or a container element (a child scope) is <b>not</b> refused, and delegation to a
 * <em>different</em> method returning the same type is <b>not</b> refused. The guard is per-binding and purely
 * structural: it compares the spec's neutral {@link OperationSpec#getCallTarget() call target} to the enclosing
 * {@link MethodScope}'s method by signature (name + parameter types) and checks whether a bound port is that method's
 * own parameter-root {@link Value} (a {@code LEAF} {@link SourceLocation}). It never inspects the label, and it needs
 * no {@code CallableMethods}/{@code ResolveCtx} change. A cohesive collaborator the driver delegates to (mirroring
 * {@link SourceCandidates}), so the driver stays the work-list dispatch + landing site.
 */
final class SelfCallGuard {

    /** Whether landing {@code spec} bound by {@code ports} in {@code scope} would be a whole-parameter self-call. */
    boolean refuses(final Scope scope, final OperationSpec spec, final List<PortBinding> ports) {
        if (!(scope instanceof MethodScope) || !spec.getCallTarget().isPresent()) {
            return false;
        }
        final var method = ((MethodScope) scope).getMethod();
        if (!signature(spec.getCallTarget().get()).equals(signature(method))) {
            return false;
        }
        final var parameterRoots = parameterRootLocations(method);
        return ports.stream()
                .anyMatch(binding -> parameterRoots.contains(binding.getSource().getLocation()));
    }

    /** The whole-parameter source locations of {@code method}: a {@code LEAF} {@link SourceLocation} per parameter. */
    private static Set<Location> parameterRootLocations(final ExecutableElement method) {
        return method.getParameters().stream()
                .map(parameter -> (Location) new SourceLocation(
                        AccessPath.of(parameter.getSimpleName().toString())))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String signature(final ExecutableElement method) {
        final var params = method.getParameters().stream()
                .map(parameter -> parameter.asType().toString())
                .collect(Collectors.joining(","));
        return method.getSimpleName() + "(" + params + ")";
    }
}
