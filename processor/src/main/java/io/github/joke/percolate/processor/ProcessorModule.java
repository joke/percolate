package io.github.joke.percolate.processor;

import dagger.Module;
import dagger.Provides;
import io.github.joke.percolate.processor.expand.BridgeSourceToTargetPhase;
import io.github.joke.percolate.processor.expand.ExpandStage;
import io.github.joke.percolate.processor.expand.ExpansionPhase;
import io.github.joke.percolate.processor.expand.ResolveSourceChainsPhase;
import io.github.joke.percolate.processor.expand.ResolveTargetChainsPhase;
import io.github.joke.percolate.processor.graph.DotRenderer;
import io.github.joke.percolate.processor.spi.Bridge;
import io.github.joke.percolate.processor.spi.GroupTarget;
import io.github.joke.percolate.processor.spi.ResolveCtx;
import io.github.joke.percolate.processor.spi.SourceStep;
import io.github.joke.percolate.processor.validate.ValidateMarkersPhase;
import io.github.joke.percolate.processor.validate.ValidatePathsPhase;
import io.github.joke.percolate.processor.validate.ValidateRealisationStage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@Module
@RequiredArgsConstructor(onConstructor_ = @Inject)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
final class ProcessorModule {

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
    }
}
