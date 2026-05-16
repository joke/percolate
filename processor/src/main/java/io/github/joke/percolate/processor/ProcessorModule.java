package io.github.joke.percolate.processor;

import dagger.Module;
import dagger.Provides;
import io.github.joke.percolate.processor.graph.DotRenderer;
import io.github.joke.percolate.processor.stages.Stage;
import io.github.joke.percolate.processor.stages.discover.DiscoverAbstractMethods;
import io.github.joke.percolate.processor.stages.discover.DiscoverCallableMethods;
import io.github.joke.percolate.processor.stages.discover.DiscoverMappings;
import io.github.joke.percolate.processor.stages.dump.DumpExpandedGraph;
import io.github.joke.percolate.processor.stages.dump.DumpGraph;
import io.github.joke.percolate.processor.stages.expand.BridgeSourceToTargetPhase;
import io.github.joke.percolate.processor.stages.expand.ExpandStage;
import io.github.joke.percolate.processor.stages.expand.ExpansionPhase;
import io.github.joke.percolate.processor.stages.expand.ResolveSourceChainsPhase;
import io.github.joke.percolate.processor.stages.expand.ResolveTargetChainsPhase;
import io.github.joke.percolate.processor.stages.seed.SeedGraph;
import io.github.joke.percolate.processor.stages.validate.ValidateMarkersPhase;
import io.github.joke.percolate.processor.stages.validate.ValidateNoDuplicateTargets;
import io.github.joke.percolate.processor.stages.validate.ValidatePathsPhase;
import io.github.joke.percolate.processor.stages.validate.ValidateRealisationStage;
import io.github.joke.percolate.processor.stages.validate.ValidateSourceParameters;
import io.github.joke.percolate.spi.Bridge;
import io.github.joke.percolate.spi.CallableMethods;
import io.github.joke.percolate.spi.GroupTarget;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.SourceStep;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

@Module
@RequiredArgsConstructor(onConstructor_ = @Inject)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class ProcessorModule {

    private static final ThreadLocal<MapperContext> CURRENT_CONTEXT = new ThreadLocal<>();

    private final ProcessingEnvironment processingEnvironment;

    static void setCurrentContext(final MapperContext ctx) {
        CURRENT_CONTEXT.set(ctx);
    }

    static void clearCurrentContext() {
        CURRENT_CONTEXT.remove();
    }

    @Provides
    Elements elements() {
        return processingEnvironment.getElementUtils();
    }

    @Provides
    Types types() {
        return processingEnvironment.getTypeUtils();
    }

    @Provides
    Messager messager() {
        return processingEnvironment.getMessager();
    }

    @Provides
    Filer filer() {
        return processingEnvironment.getFiler();
    }

    @Provides
    ProcessorOptions processorOptions() {
        return ProcessorOptions.from(processingEnvironment.getOptions());
    }

    @Provides
    DotRenderer dotRenderer() {
        return new DotRenderer();
    }

    @Provides
    DiscoverAbstractMethods discoverAbstractMethods(final Elements elements, final Types types) {
        return new DiscoverAbstractMethods(elements, types);
    }

    @Provides
    DiscoverMappings discoverMappings(final Elements elements) {
        return new DiscoverMappings(elements);
    }

    @Provides
    DiscoverCallableMethods discoverCallableMethods(final Elements elements, final Types types) {
        return new DiscoverCallableMethods(elements, types);
    }

    @Provides
    ValidateNoDuplicateTargets validateNoDuplicateTargets(final Diagnostics diagnostics) {
        return new ValidateNoDuplicateTargets(diagnostics);
    }

    @Provides
    ValidateSourceParameters validateSourceParameters(final Diagnostics diagnostics) {
        return new ValidateSourceParameters(diagnostics);
    }

    @Provides
    SeedGraph seedGraph() {
        return new SeedGraph();
    }

    @Provides
    DumpGraph dumpGraph(
            final Filer filer,
            final Diagnostics diagnostics,
            final ProcessorOptions processorOptions,
            final DotRenderer dotRenderer) {
        return new DumpGraph(filer, diagnostics, processorOptions, dotRenderer);
    }

    public static ExpandStage assembleExpansionPipeline(
            final List<Bridge> bridges,
            final List<SourceStep> sourceSteps,
            final List<GroupTarget> groupTargets,
            final ResolveCtx resolveCtx,
            final Diagnostics diagnostics) {
        final var sourcePhase = new ResolveSourceChainsPhase(sourceSteps, resolveCtx);
        final var targetPhase = new ResolveTargetChainsPhase(groupTargets, resolveCtx);
        final var bridgePhase = new BridgeSourceToTargetPhase(bridges, resolveCtx);
        final var phases = List.<ExpansionPhase>of(sourcePhase, targetPhase, bridgePhase);
        return new ExpandStage(phases, diagnostics);
    }

    @Provides
    ExpandStage expandStage(
            final List<Bridge> bridges,
            final List<SourceStep> sourceSteps,
            final List<GroupTarget> groupTargets,
            final ResolveCtx resolveCtx,
            final Diagnostics diagnostics) {
        return assembleExpansionPipeline(bridges, sourceSteps, groupTargets, resolveCtx, diagnostics);
    }

    @Provides
    ValidateRealisationStage validateRealisationStage(
            final ValidateMarkersPhase markersPhase,
            final ValidatePathsPhase pathsPhase,
            final Diagnostics diagnostics) {
        return new ValidateRealisationStage(markersPhase, pathsPhase, diagnostics);
    }

    @Provides
    ValidateMarkersPhase validateMarkersPhase(final Diagnostics diagnostics) {
        return new ValidateMarkersPhase(diagnostics);
    }

    @Provides
    ValidatePathsPhase validatePathsPhase(final Diagnostics diagnostics) {
        return new ValidatePathsPhase(diagnostics);
    }

    @Provides
    DumpExpandedGraph dumpExpandedGraph(
            final Filer filer,
            final Diagnostics diagnostics,
            final ProcessorOptions processorOptions,
            final DotRenderer dotRenderer) {
        return new DumpExpandedGraph(filer, diagnostics, processorOptions, dotRenderer);
    }

    @Provides
    @Named("discover")
    static List<Stage> discoverStages(
            final DiscoverAbstractMethods discoverAbstractMethods,
            final DiscoverMappings discoverMappings,
            final DiscoverCallableMethods discoverCallableMethods) {
        return List.of(discoverAbstractMethods, discoverMappings, discoverCallableMethods);
    }

    @Provides
    static List<Stage> stages(
            @Named("discover") final List<Stage> discoverStages,
            final ValidateNoDuplicateTargets validateNoDuplicateTargets,
            final ValidateSourceParameters validateSourceParameters,
            final SeedGraph seedGraph,
            final DumpGraph dumpGraph,
            final ExpandStage expandStage,
            final ValidateRealisationStage validateRealisationStage,
            final DumpExpandedGraph dumpExpandedGraph) {
        final var all = new ArrayList<Stage>(discoverStages);
        all.add(validateNoDuplicateTargets);
        all.add(validateSourceParameters);
        all.add(seedGraph);
        all.add(dumpGraph);
        all.add(expandStage);
        all.add(validateRealisationStage);
        all.add(dumpExpandedGraph);
        return List.copyOf(all);
    }

    @Singleton
    @Provides
    static List<SourceStep> sourceSteps() {
        return StreamSupport.stream(
                        ServiceLoader.load(SourceStep.class, ProcessorModule.class.getClassLoader())
                                .spliterator(),
                        false)
                .sorted((a, b) -> a.getClass().getName().compareTo(b.getClass().getName()))
                .collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    @Singleton
    @Provides
    static List<GroupTarget> groupTargets() {
        return StreamSupport.stream(
                        ServiceLoader.load(GroupTarget.class, ProcessorModule.class.getClassLoader())
                                .spliterator(),
                        false)
                .sorted((a, b) -> a.getClass().getName().compareTo(b.getClass().getName()))
                .collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    @Singleton
    @Provides
    static List<Bridge> bridgeStrategies() {
        return StreamSupport.stream(
                        ServiceLoader.load(Bridge.class, ProcessorModule.class.getClassLoader())
                                .spliterator(),
                        false)
                .sorted((a, b) -> a.getClass().getName().compareTo(b.getClass().getName()))
                .collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    @Provides
    static ResolveCtx resolveCtx(final Types types, final Elements elements) {
        return new CompileResolveCtx(elements, types);
    }

    @RequiredArgsConstructor
    private static final class CompileResolveCtx implements ResolveCtx {
        private final Elements elemElements;
        private final Types elemTypes;

        @Override
        public Types types() {
            return elemTypes;
        }

        @Override
        public Elements elements() {
            return elemElements;
        }

        @Override
        public @Nullable TypeElement mapperType() {
            final var ctx = CURRENT_CONTEXT.get();
            return ctx != null ? ctx.getMapperType() : null;
        }

        @Override
        public @Nullable ExecutableElement currentMethod() {
            final var ctx = CURRENT_CONTEXT.get();
            return ctx != null ? ctx.getCurrentMethod() : null;
        }

        @Override
        public @Nullable CallableMethods callableMethods() {
            final var ctx = CURRENT_CONTEXT.get();
            return ctx != null ? ctx.getCallableMethods() : null;
        }
    }
}
