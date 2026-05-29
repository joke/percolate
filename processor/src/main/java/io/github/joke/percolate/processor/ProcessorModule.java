package io.github.joke.percolate.processor;

import dagger.Module;
import dagger.Provides;
import io.github.joke.percolate.processor.graph.DotRenderer;
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver;
import io.github.joke.percolate.processor.nullability.NullabilityAnnotations;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.processor.stages.Stage;
import io.github.joke.percolate.processor.stages.discover.DiscoverAbstractMethods;
import io.github.joke.percolate.processor.stages.discover.DiscoverCallableMethods;
import io.github.joke.percolate.processor.stages.discover.DiscoverMappings;
import io.github.joke.percolate.processor.stages.dump.DumpFullGraph;
import io.github.joke.percolate.processor.stages.dump.DumpGraph;
import io.github.joke.percolate.processor.stages.dump.DumpPlan;
import io.github.joke.percolate.processor.stages.dump.DumpTransforms;
import io.github.joke.percolate.processor.stages.expand.ExpandGroupsPhase;
import io.github.joke.percolate.processor.stages.expand.ExpandStage;
import io.github.joke.percolate.processor.stages.expand.ExpansionPhase;
import io.github.joke.percolate.processor.stages.expand.ResolveTargetChainsPhase;
import io.github.joke.percolate.processor.stages.generate.AssembleMapperType;
import io.github.joke.percolate.processor.stages.generate.BuildMethodBodies;
import io.github.joke.percolate.processor.stages.generate.GenerateStage;
import io.github.joke.percolate.processor.stages.seed.SeedGraph;
import io.github.joke.percolate.processor.stages.validate.ValidateNoDuplicateTargets;
import io.github.joke.percolate.processor.stages.validate.ValidateSourceParameters;
import io.github.joke.percolate.spi.Bridge;
import io.github.joke.percolate.spi.CallableMethods;
import io.github.joke.percolate.spi.GroupTarget;
import io.github.joke.percolate.spi.PathSegmentResolver;
import io.github.joke.percolate.spi.ResolveCtx;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.ArrayList;
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
    @Singleton
    NullabilityAnnotations nullabilityAnnotations(final ProcessorOptions processorOptions) {
        final var defaults = NullabilityAnnotations.jspecifyDefaults();
        final var custom = processorOptions.getCustomNullableAnnotations();
        if (custom.isEmpty()) {
            return defaults;
        }
        final var merged = new java.util.HashSet<String>(defaults.getNullableFqns());
        merged.addAll(custom);
        return new NullabilityAnnotations(merged, defaults.getMarkedFqns(), defaults.getUnmarkedFqns());
    }

    @Provides
    @Singleton
    NullabilityResolver nullabilityResolver(final NullabilityAnnotations annotations, final Elements elements) {
        return new JspecifyNullabilityResolver(annotations, elements);
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
            final List<GroupTarget> groupTargets,
            final List<PathSegmentResolver> pathSegmentResolvers,
            final ResolveCtx resolveCtx,
            final NullabilityResolver nullabilityResolver) {
        final var targetPhase = new ResolveTargetChainsPhase(groupTargets, resolveCtx);
        final var groupsPhase =
                ExpandGroupsPhase.create(bridges, groupTargets, pathSegmentResolvers, resolveCtx, nullabilityResolver);
        final var phases = List.<ExpansionPhase>of(targetPhase, groupsPhase);
        return new ExpandStage(phases);
    }

    @Provides
    ExpandStage expandStage(
            final List<Bridge> bridges,
            final List<GroupTarget> groupTargets,
            final List<PathSegmentResolver> pathSegmentResolvers,
            final ResolveCtx resolveCtx,
            final NullabilityResolver nullabilityResolver) {
        return assembleExpansionPipeline(bridges, groupTargets, pathSegmentResolvers, resolveCtx, nullabilityResolver);
    }

    @Provides
    DumpFullGraph dumpFullGraph(
            final Filer filer,
            final Diagnostics diagnostics,
            final ProcessorOptions processorOptions,
            final DotRenderer dotRenderer) {
        return new DumpFullGraph(filer, diagnostics, processorOptions, dotRenderer);
    }

    @Provides
    DumpTransforms dumpTransforms(
            final Filer filer,
            final Diagnostics diagnostics,
            final ProcessorOptions processorOptions,
            final DotRenderer dotRenderer) {
        return new DumpTransforms(filer, diagnostics, processorOptions, dotRenderer);
    }

    @Provides
    DumpPlan dumpPlan(
            final Filer filer,
            final Diagnostics diagnostics,
            final ProcessorOptions processorOptions,
            final DotRenderer dotRenderer) {
        return new DumpPlan(filer, diagnostics, processorOptions, dotRenderer);
    }

    @Provides
    BuildMethodBodies buildMethodBodies(final NullabilityResolver nullabilityResolver) {
        return new BuildMethodBodies(nullabilityResolver);
    }

    @Provides
    AssembleMapperType assembleMapperType(final Filer filer, final Elements elements) {
        return new AssembleMapperType(filer, elements);
    }

    @Provides
    GenerateStage generateStage(
            final Diagnostics diagnostics,
            final BuildMethodBodies buildMethodBodies,
            final AssembleMapperType assembleMapperType) {
        return new GenerateStage(diagnostics, buildMethodBodies, assembleMapperType);
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
    @SuppressWarnings("PMD.ExcessiveParameterList")
    static List<Stage> stages(
            @Named("discover") final List<Stage> discoverStages,
            final ValidateNoDuplicateTargets validateNoDuplicateTargets,
            final ValidateSourceParameters validateSourceParameters,
            final SeedGraph seedGraph,
            final DumpGraph dumpGraph,
            final ExpandStage expandStage,
            final DumpFullGraph dumpFullGraph,
            final DumpTransforms dumpTransforms,
            final DumpPlan dumpPlan,
            final io.github.joke.percolate.processor.stages.validate.RealisationDiagnosticsStage
                    realisationDiagnosticsStage,
            final GenerateStage generateStage) {
        final var all = new ArrayList<Stage>(discoverStages);
        all.add(validateNoDuplicateTargets);
        all.add(validateSourceParameters);
        all.add(seedGraph);
        all.add(dumpGraph);
        all.add(expandStage);
        all.add(dumpFullGraph);
        all.add(dumpTransforms);
        all.add(dumpPlan);
        all.add(realisationDiagnosticsStage);
        all.add(generateStage);
        return List.copyOf(all);
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

    @Singleton
    @Provides
    static List<PathSegmentResolver> pathSegmentResolvers() {
        return StreamSupport.stream(
                        ServiceLoader.load(PathSegmentResolver.class, ProcessorModule.class.getClassLoader())
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
