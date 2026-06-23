package io.github.joke.percolate.processor;

import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.processor.model.GoalSpec;
import io.github.joke.percolate.processor.model.MapperMappings;
import io.github.joke.percolate.processor.model.MapperShape;
import io.github.joke.percolate.spi.CallableMethods;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.TypeElement;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Getter
@Setter
public final class MapperContext {
    private final TypeElement mapperType;
    private @Nullable MapperShape shape;
    private @Nullable MapperMappings mappings;
    private @Nullable MapperGraph graph;
    private @Nullable CallableMethods callableMethods;

    /**
     * The recorded realisation outcome: the closest-miss "no plan" messages for this mapper's
     * unreachable return roots, empty when the mapper is fully realisable. {@code RealisationDiagnosticsStage}
     * records it here instead of emitting; {@code MapperStep} emits it on the deferral fixpoint.
     */
    private List<String> unsatisfiedRealisation = List.of();

    /** Per-method declared-bindings goal specs, keyed by the method's {@link Scope} (design D9). */
    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded per-mapper context
    private final Map<Scope, GoalSpec> goalSpecs = new HashMap<>();
}
