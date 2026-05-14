package io.github.joke.percolate.test;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.ProcessorModule;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.spi.Bridge;
import io.github.joke.percolate.processor.spi.GroupTarget;
import io.github.joke.percolate.processor.spi.SourceStep;
import io.github.joke.percolate.processor.stages.validate.ValidatePathsPhase;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

public final class ExpansionHarness {

    private static final TypeElement MAPPER_PLACEHOLDER = Objects.requireNonNull(
            TypeUniverse.elements().getTypeElement("java.lang.Object"),
            "java.lang.Object not resolvable via TypeUniverse");

    private ExpansionHarness() {}

    /**
     * Expands the given seed graph using strategies loaded via {@link ServiceLoader} from the
     * current thread's context class loader. This is the default mode, used by Spock specs
     * and production-parity scenarios.
     *
     * <p>Auto-invariants enforced on every call:
     *
     * <ul>
     *   <li><b>Convergence</b> — expansion completes within {@code MAX_EXPANSION_ROUNDS}
     *   <li><b>Idempotence</b> — running expansion again produces zero new edges
     *   <li><b>Identity collapse</b> — no two nodes share (scope, location, type)
     *   <li><b>No orphan REALISED nodes</b> — every REALISED node is reachable from a SEED endpoint
     * </ul>
     *
     * @param seed the seed graph to expand
     * @return the expansion result containing the expanded graph and diagnostics
     */
    public static ExpansionResult expand(final MapperGraph seed) {
        return expand(seed, loadService(Bridge.class), loadService(SourceStep.class), loadService(GroupTarget.class));
    }

    /**
     * Expands the given seed graph using the explicitly provided strategy lists. This mode
     * bypasses {@link ServiceLoader} and is used by property tests and isolation scenarios.
     *
     * <p>Auto-invariants enforced on every call (same as SPI mode). Use
     * {@link ExpansionResult} methods to check invariant results.
     *
     * @param seed the seed graph to expand
     * @param bridges bridge strategies
     * @param sourceSteps source step strategies
     * @param groupTargets group target strategies
     * @return the expansion result containing the expanded graph and diagnostics
     */
    public static ExpansionResult expand(
            final MapperGraph seed,
            final List<Bridge> bridges,
            final List<SourceStep> sourceSteps,
            final List<GroupTarget> groupTargets) {
        final var errorMessages = new CopyOnWriteArrayList<String>();
        final var messager = new Messager() {
            @Override
            public void printMessage(
                    final Diagnostic.Kind kind,
                    final CharSequence msg,
                    final Element element,
                    final AnnotationMirror mirror,
                    final AnnotationValue value) {
                if (kind == Diagnostic.Kind.ERROR) {
                    errorMessages.add(msg.toString());
                }
            }

            @Override
            public void printMessage(
                    final Diagnostic.Kind kind,
                    final CharSequence msg,
                    final Element element,
                    final AnnotationMirror mirror) {
                if (kind == Diagnostic.Kind.ERROR) {
                    errorMessages.add(msg.toString());
                }
            }

            @Override
            public void printMessage(final Diagnostic.Kind kind, final CharSequence msg) {}

            @Override
            public void printMessage(final Diagnostic.Kind kind, final CharSequence msg, final Element element) {}
        };

        final var diagnostics = new Diagnostics(messager);
        final var resolveCtx = HarnessResolveCtx.create();

        final var stage =
                ProcessorModule.assembleExpansionPipeline(bridges, sourceSteps, groupTargets, resolveCtx, diagnostics);

        final var ctx = new MapperContext(MAPPER_PLACEHOLDER);
        ctx.setGraph(seed);
        stage.run(ctx);

        final var expandedGraph = ctx.getGraph();
        if (expandedGraph == null) {
            return ExpansionResult.of(new MapperGraph(), List.copyOf(errorMessages), 0, false, MAPPER_PLACEHOLDER);
        }

        final var validatePaths = new ValidatePathsPhase(diagnostics);
        validatePaths.apply(expandedGraph, MAPPER_PLACEHOLDER);

        final var messages = List.copyOf(errorMessages);

        final var converged = messages.stream().noneMatch(m -> m.contains("did not converge"));

        return ExpansionResult.of(expandedGraph, messages, 1, converged, MAPPER_PLACEHOLDER);
    }

    private static <T> List<T> loadService(final Class<T> service) {
        return ServiceLoader.load(service, ExpansionHarness.class.getClassLoader()).stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparing(t -> t.getClass().getName()))
                .collect(Collectors.toUnmodifiableList());
    }
}
