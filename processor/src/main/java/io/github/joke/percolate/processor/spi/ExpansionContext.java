package io.github.joke.percolate.processor.spi;

import io.github.joke.percolate.MapOptKey;
import java.util.Map;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * Per-demand context passed to {@link ValueExpansionStrategy#expand(ExpansionDemand,
 * ExpansionContext)}.
 *
 * <p>Strategies consult {@link #getTypes()} / {@link #getElements()} for type-system queries and
 * {@link #getRoutableIndex()} for intra-mapper routing. The current mapper's {@code TypeElement}
 * and the mapping method being expanded are exposed for diagnostics and annotation lookups.
 *
 * <p>Budget tracking and the per-path cycle guard are managed by {@code BuildValueGraphStage}
 * outside the strategy contract — strategies MUST NOT attempt to mutate expansion state
 * directly; they may only return a {@link Subgraph} or {@code Optional.empty()}.
 */
@Value
public class ExpansionContext {
    Types types;
    Elements elements;
    TypeElement mapperType;
    ExecutableElement currentMethod;
    Map<MapOptKey, String> options;

    /**
     * Intra-mapper routing index keyed on {@code (input, output)} type pairs. Values are the
     * {@link ExecutableElement}s of {@code @Routable} default methods on the mapper.
     */
    Map<RoutableKey, ExecutableElement> routableIndex;

    /**
     * The {@code @Map(using = "...")} method name set on the originating
     * {@code MappingAssignment}, or {@code null} when this demand does not carry an explicit
     * routing hint.
     */
    @Nullable String using;

    /** Composite key for {@link #getRoutableIndex()} entries. */
    @Value
    public static class RoutableKey {
        TypeMirror inputType;
        TypeMirror outputType;
    }
}
