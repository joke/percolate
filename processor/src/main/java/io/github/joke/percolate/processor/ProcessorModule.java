package io.github.joke.percolate.processor;

import dagger.Module;
import dagger.Provides;
import io.github.joke.percolate.processor.graph.DotRenderer;
import io.github.joke.percolate.processor.spi.Bridge;
import io.github.joke.percolate.processor.spi.CallableMethods;
import io.github.joke.percolate.processor.spi.GroupTarget;
import io.github.joke.percolate.processor.spi.ResolveCtx;
import io.github.joke.percolate.processor.spi.SourceStep;
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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@Module
@RequiredArgsConstructor(onConstructor_ = @Inject)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
final class ProcessorModule {

    private static final ThreadLocal<MapperContext> CURRENT_CONTEXT = new ThreadLocal<>();

    static void setCurrentContext(final MapperContext ctx) {
        CURRENT_CONTEXT.set(ctx);
    }

    static void clearCurrentContext() {
        CURRENT_CONTEXT.remove();
    }

    private final ProcessingEnvironment processingEnvironment;

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
    DiscoverAbstractMethods discoverAbstractMethods(Elements elements, Types types) {
        return new DiscoverAbstractMethods(elements, types);
    }

    @Provides
    DiscoverMappings discoverMappings(Elements elements) {
        return new DiscoverMappings(elements);
    }

    @Provides
    DiscoverCallableMethods discoverCallableMethods(Elements elements, Types types) {
        return new DiscoverCallableMethods(elements, types);
    }

    @Provides
    ValidateNoDuplicateTargets validateNoDuplicateTargets(Diagnostics diagnostics) {
        return new ValidateNoDuplicateTargets(diagnostics);
    }

    @Provides
    ValidateSourceParameters validateSourceParameters(Diagnostics diagnostics) {
        return new ValidateSourceParameters(diagnostics);
    }

    @Provides
    SeedGraph seedGraph() {
        return new SeedGraph();
    }

    @Provides
    DumpGraph dumpGraph(
            Filer filer, Diagnostics diagnostics, ProcessorOptions processorOptions, DotRenderer dotRenderer) {
        return new DumpGraph(filer, diagnostics, processorOptions, dotRenderer);
    }

    @Provides
    ExpandStage expandStage(List<ExpansionPhase> phases, Diagnostics diagnostics) {
        return new ExpandStage(phases, diagnostics);
    }

    @Provides
    ValidateRealisationStage validateRealisationStage(
            ValidateMarkersPhase markersPhase, ValidatePathsPhase pathsPhase, Diagnostics diagnostics) {
        return new ValidateRealisationStage(markersPhase, pathsPhase, diagnostics);
    }

    @Provides
    ValidateMarkersPhase validateMarkersPhase(Diagnostics diagnostics) {
        return new ValidateMarkersPhase(diagnostics);
    }

    @Provides
    ValidatePathsPhase validatePathsPhase(Diagnostics diagnostics) {
        return new ValidatePathsPhase(diagnostics);
    }

    @Provides
    DumpExpandedGraph dumpExpandedGraph(
            Filer filer, Diagnostics diagnostics, ProcessorOptions processorOptions, DotRenderer dotRenderer) {
        return new DumpExpandedGraph(filer, diagnostics, processorOptions, dotRenderer);
    }

    @Provides
    ResolveSourceChainsPhase resolveSourceChainsPhase(List<SourceStep> sourceSteps, ResolveCtx resolveCtx) {
        return new ResolveSourceChainsPhase(sourceSteps, resolveCtx);
    }

    @Provides
    ResolveTargetChainsPhase resolveTargetChainsPhase(List<GroupTarget> groupTargets, ResolveCtx resolveCtx) {
        return new ResolveTargetChainsPhase(groupTargets, resolveCtx);
    }

    @Provides
    BridgeSourceToTargetPhase bridgeSourceToTargetPhase(List<Bridge> bridges, ResolveCtx resolveCtx) {
        return new BridgeSourceToTargetPhase(bridges, resolveCtx);
    }

    @Provides
    static List<ExpansionPhase> expansionPhases(
            ResolveSourceChainsPhase sourceChains,
            ResolveTargetChainsPhase targetChains,
            BridgeSourceToTargetPhase bridgePhase) {
        return List.of(sourceChains, targetChains, bridgePhase);
    }

    @Provides
    static List<Stage> stages(
            DiscoverAbstractMethods discoverAbstractMethods,
            DiscoverMappings discoverMappings,
            DiscoverCallableMethods discoverCallableMethods,
            ValidateNoDuplicateTargets validateNoDuplicateTargets,
            ValidateSourceParameters validateSourceParameters,
            SeedGraph seedGraph,
            DumpGraph dumpGraph,
            ExpandStage expandStage,
            ValidateRealisationStage validateRealisationStage,
            DumpExpandedGraph dumpExpandedGraph) {
        return List.of(
                discoverAbstractMethods,
                discoverMappings,
                discoverCallableMethods,
                validateNoDuplicateTargets,
                validateSourceParameters,
                seedGraph,
                dumpGraph,
                expandStage,
                validateRealisationStage,
                dumpExpandedGraph);
    }

    @Singleton
    @Provides
    static List<SourceStep> sourceSteps() {
        return StreamSupport.stream(
                        ServiceLoader.load(SourceStep.class, SourceStep.class.getClassLoader())
                                .spliterator(),
                        false)
                .sorted((a, b) -> a.getClass().getName().compareTo(b.getClass().getName()))
                .collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    @Singleton
    @Provides
    static List<GroupTarget> groupTargets() {
        return StreamSupport.stream(
                        ServiceLoader.load(GroupTarget.class, GroupTarget.class.getClassLoader())
                                .spliterator(),
                        false)
                .sorted((a, b) -> a.getClass().getName().compareTo(b.getClass().getName()))
                .collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    @Singleton
    @Provides
    static List<Bridge> bridgeStrategies() {
        return StreamSupport.stream(
                        ServiceLoader.load(Bridge.class, Bridge.class.getClassLoader())
                                .spliterator(),
                        false)
                .sorted((a, b) -> a.getClass().getName().compareTo(b.getClass().getName()))
                .collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    @Provides
    static ResolveCtx resolveCtx(Types types, Elements elements) {
        return new CompileResolveCtx(elements, types);
    }

    @RequiredArgsConstructor
    private static final class CompileResolveCtx implements io.github.joke.percolate.processor.spi.ResolveCtx {
        private final Elements elements;
        private final Types types;

        @Override
        public Types types() {
            return types;
        }

        @Override
        public Elements elements() {
            return elements;
        }

        @Override
        public @Nullable TypeElement mapperType() {
            final MapperContext ctx = CURRENT_CONTEXT.get();
            return ctx != null ? ctx.getMapperType() : null;
        }

        @Override
        public @Nullable ExecutableElement currentMethod() {
            final MapperContext ctx = CURRENT_CONTEXT.get();
            return ctx != null ? ctx.getCurrentMethod() : null;
        }

        @Override
        public @Nullable CallableMethods callableMethods() {
            final MapperContext ctx = CURRENT_CONTEXT.get();
            return ctx != null ? ctx.getCallableMethods() : null;
        }
    }
}
