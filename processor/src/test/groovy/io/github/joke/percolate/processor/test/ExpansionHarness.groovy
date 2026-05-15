package io.github.joke.percolate.processor.test

import io.github.joke.percolate.processor.Diagnostics
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.ProcessorModule
import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.spi.Bridge
import io.github.joke.percolate.spi.GroupTarget
import io.github.joke.percolate.spi.SourceStep
import io.github.joke.percolate.spi.test.HarnessResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse
import io.github.joke.percolate.processor.stages.validate.ValidatePathsPhase

import javax.annotation.processing.Messager
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import java.util.concurrent.CopyOnWriteArrayList

final class ExpansionHarness {

    private static final TypeElement MAPPER_PLACEHOLDER = Objects.requireNonNull(
            TypeUniverse.element('java.lang.Object'), 'java.lang.Object not resolvable via TypeUniverse')
    private static final Object EXPAND_LOCK = new Object()

    private ExpansionHarness() {}

    /**
     * Expands the given seed graph using the explicitly provided strategy lists. This mode
     * bypasses ServiceLoader and is used by property tests and isolation scenarios.
     */
    static ExpansionResult expand(
            final MapperGraph seed,
            final List<Bridge> bridges,
            final List<SourceStep> sourceSteps,
            final List<GroupTarget> groupTargets) {
        synchronized (EXPAND_LOCK) {
            expandLocked(seed, bridges, sourceSteps, groupTargets)
        }
    }

    private static ExpansionResult expandLocked(
            final MapperGraph seed,
            final List<Bridge> bridges,
            final List<SourceStep> sourceSteps,
            final List<GroupTarget> groupTargets) {
        final errorMessages = new CopyOnWriteArrayList<String>()
        final messager = new Messager() {
            @Override
            void printMessage(
                    final Diagnostic.Kind kind,
                    final CharSequence msg,
                    final Element element,
                    final AnnotationMirror mirror,
                    final AnnotationValue value) {
                if (kind == Diagnostic.Kind.ERROR) {
                    errorMessages.add(msg.toString())
                }
            }

            @Override
            void printMessage(
                    final Diagnostic.Kind kind,
                    final CharSequence msg,
                    final Element element,
                    final AnnotationMirror mirror) {
                if (kind == Diagnostic.Kind.ERROR) {
                    errorMessages.add(msg.toString())
                }
            }

            @Override
            void printMessage(final Diagnostic.Kind kind, final CharSequence msg) {}

            @Override
            void printMessage(final Diagnostic.Kind kind, final CharSequence msg, final Element element) {}
        }

        final diagnostics = new Diagnostics(messager)
        final resolveCtx = HarnessResolveCtx.create()

        final stage =
                ProcessorModule.assembleExpansionPipeline(bridges, sourceSteps, groupTargets, resolveCtx, diagnostics)

        final ctx = new MapperContext(MAPPER_PLACEHOLDER)
        ctx.graph = seed
        stage.run(ctx)

        final expandedGraph = ctx.graph
        if (expandedGraph == null) {
            return ExpansionResult.of(new MapperGraph(), List.copyOf(errorMessages), 0, false, MAPPER_PLACEHOLDER)
        }

        final validatePaths = new ValidatePathsPhase(diagnostics)
        validatePaths.apply(expandedGraph, MAPPER_PLACEHOLDER)

        final messages = List.copyOf(errorMessages)
        final converged = messages.every { !it.contains('did not converge') }

        ExpansionResult.of(expandedGraph, messages, 1, converged, MAPPER_PLACEHOLDER)
    }
}
