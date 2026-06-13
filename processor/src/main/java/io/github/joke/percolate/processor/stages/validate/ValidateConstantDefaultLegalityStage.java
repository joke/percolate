package io.github.joke.percolate.processor.stages.validate;

import static java.util.Objects.requireNonNull;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.AccessPath;
import io.github.joke.percolate.processor.graph.Location;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.graph.TargetPath;
import io.github.joke.percolate.processor.graph.Value;
import io.github.joke.percolate.processor.model.MappingDirective;
import io.github.joke.percolate.processor.stages.Stage;
import io.github.joke.percolate.spi.LiteralCoercion;
import io.github.joke.percolate.spi.Nullability;
import jakarta.inject.Inject;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Late, diagnostic-only legality checks for constants and defaults, run after nullability inference (so node typing
 * and stamping are final). Against the resolved target type it emits:
 *
 * <ul>
 *   <li>a <strong>coercion-failure</strong> error when a {@code constant}/{@code defaultValue} cannot be coerced to
 *       the resolved target type (e.g. {@code "cannot coerce 'abc' to int"}), positioned at the offending value;</li>
 *   <li>a <strong>dead-default</strong> error when a {@code defaultValue}'s source resolves to a {@code NON_NULL}
 *       reference scalar or a primitive — it can never be absent, so the default can never fire. Nullable and
 *       {@code Optional} sources are accepted.</li>
 * </ul>
 *
 * Strategies stay myopic and side-effect-free; this stage owns the targeted messages (design D7).
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidateConstantDefaultLegalityStage implements Stage {

    private final Diagnostics diagnostics;

    @Override
    public void run(final MapperContext ctx) {
        final var mappings = ctx.getMappings();
        final var graph = ctx.getGraph();
        if (mappings == null || graph == null) {
            return;
        }
        for (final var method : mappings.getMethods()) {
            final var scope = new MethodScope(method.getMethod());
            for (final var directive : method.getDirectives()) {
                checkDirective(directive, method.getMethod(), scope, graph);
            }
        }
    }

    private void checkDirective(
            final MappingDirective directive,
            final ExecutableElement method,
            final MethodScope scope,
            final MapperGraph graph) {
        if (directive.hasConstant()) {
            checkConstant(directive, method, scope, graph);
        } else if (directive.hasDefaultValue()) {
            checkDefault(directive, method, scope, graph);
        }
    }

    private void checkConstant(
            final MappingDirective directive,
            final ExecutableElement method,
            final MethodScope scope,
            final MapperGraph graph) {
        final var target = targetType(graph, scope, directive.getTarget());
        if (target == null) {
            return;
        }
        final var constant = requireNonNull(directive.getConstant());
        if (LiteralCoercion.coerce(constant, target).isEmpty()) {
            diagnostics.error(
                    method,
                    directive.getMirror(),
                    directive.getConstantValue(),
                    "cannot coerce '" + constant + "' to " + typeName(target));
        }
    }

    private void checkDefault(
            final MappingDirective directive,
            final ExecutableElement method,
            final MethodScope scope,
            final MapperGraph graph) {
        final var defaultValue = requireNonNull(directive.getDefaultValue());
        final var target = targetType(graph, scope, directive.getTarget());
        if (target != null && LiteralCoercion.coerce(defaultValue, target).isEmpty()) {
            diagnostics.error(
                    method,
                    directive.getMirror(),
                    directive.getDefaultValueValue(),
                    "cannot coerce '" + defaultValue + "' to " + typeName(target));
            return;
        }
        final var source = sourceNode(graph, scope, directive.getSource());
        if (source != null && isDeadDefault(source)) {
            diagnostics.error(
                    method,
                    directive.getMirror(),
                    directive.getDefaultValueValue(),
                    "@Map 'defaultValue' can never fire: source '" + directive.getSource() + "' is never absent");
        }
    }

    /** A default is dead when its source can never be absent: a primitive, or a {@code NON_NULL} non-{@code Optional}. */
    private static boolean isDeadDefault(final Value source) {
        return source.getType()
                .map(type -> type.getKind().isPrimitive() || neverAbsentReference(type, source))
                .orElse(false);
    }

    private static boolean neverAbsentReference(final TypeMirror type, final Value source) {
        return !isOptional(type) && source.getNullness().orElse(Nullability.UNKNOWN) == Nullability.NON_NULL;
    }

    @Nullable
    private static TypeMirror targetType(final MapperGraph graph, final MethodScope scope, final String target) {
        final var value = findTypedValue(graph, scope, new TargetLocation(new TargetPath(splitPath(target))));
        return value == null ? null : value.getType().orElse(null);
    }

    @Nullable
    private static Value sourceNode(final MapperGraph graph, final MethodScope scope, final @Nullable String source) {
        if (source == null) {
            return null;
        }
        return findTypedValue(graph, scope, new SourceLocation(new AccessPath(splitPath(source))));
    }

    @Nullable
    private static Value findTypedValue(final MapperGraph graph, final MethodScope scope, final Location loc) {
        return graph.valuesIn(scope)
                .filter(value -> value.getLoc().equals(loc))
                .filter(value -> value.getType().isPresent())
                .findFirst()
                .orElse(null);
    }

    private static boolean isOptional(final TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        final var element = ((DeclaredType) type).asElement();
        return element instanceof TypeElement
                && ((TypeElement) element).getQualifiedName().contentEquals("java.util.Optional");
    }

    private static String typeName(final TypeMirror type) {
        if (type instanceof DeclaredType) {
            final var element = ((DeclaredType) type).asElement();
            if (element instanceof TypeElement) {
                return ((TypeElement) element).getSimpleName().toString();
            }
        }
        return type.toString();
    }

    private static List<String> splitPath(final String path) {
        if (path.isEmpty()) {
            return List.of();
        }
        return List.of(path.split("\\.", -1));
    }
}
