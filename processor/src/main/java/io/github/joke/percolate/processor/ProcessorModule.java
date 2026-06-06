package io.github.joke.percolate.processor;

import dagger.Module;
import dagger.Provides;
import io.github.joke.percolate.processor.graph.DotRenderer;
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver;
import io.github.joke.percolate.processor.nullability.NullabilityAnnotations;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.processor.stages.Stage;
import io.github.joke.percolate.processor.stages.discover.DiscoverAbstractMethodsStage;
import io.github.joke.percolate.processor.stages.discover.DiscoverCallableMethodsStage;
import io.github.joke.percolate.processor.stages.discover.DiscoverMappingsStage;
import io.github.joke.percolate.processor.stages.dump.DumpFullGraphStage;
import io.github.joke.percolate.processor.stages.dump.DumpGraphStage;
import io.github.joke.percolate.processor.stages.dump.DumpPlanStage;
import io.github.joke.percolate.processor.stages.dump.DumpTransformsStage;
import io.github.joke.percolate.processor.stages.dump.GraphDumpWriter;
import io.github.joke.percolate.processor.stages.expand.ExpandGroupsPhase;
import io.github.joke.percolate.processor.stages.expand.ExpandStage;
import io.github.joke.percolate.processor.stages.expand.ExpansionPhase;
import io.github.joke.percolate.processor.stages.generate.AssembleMapperType;
import io.github.joke.percolate.processor.stages.generate.BuildMethodBodies;
import io.github.joke.percolate.processor.stages.generate.GenerateStage;
import io.github.joke.percolate.processor.stages.seed.SeedStage;
import io.github.joke.percolate.processor.stages.validate.ValidateNoDuplicateTargetsStage;
import io.github.joke.percolate.processor.stages.validate.ValidateSourceParametersStage;
import io.github.joke.percolate.spi.CallableMethods;
import io.github.joke.percolate.spi.ExpansionStrategy;
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
    DiscoverAbstractMethodsStage discoverAbstractMethods(final Elements elements, final Types types) {
        return new DiscoverAbstractMethodsStage(elements, types);
    }

    @Provides
    DiscoverMappingsStage discoverMappings(final Elements elements) {
        return new DiscoverMappingsStage(elements);
    }

    @Provides
    DiscoverCallableMethodsStage discoverCallableMethods(final Elements elements, final Types types) {
        return new DiscoverCallableMethodsStage(elements, types);
    }

    @Provides
    ValidateNoDuplicateTargetsStage validateNoDuplicateTargets(final Diagnostics diagnostics) {
        return new ValidateNoDuplicateTargetsStage(diagnostics);
    }

    @Provides
    ValidateSourceParametersStage validateSourceParameters(final Diagnostics diagnostics) {
        return new ValidateSourceParametersStage(diagnostics);
    }

    @Provides
    SeedStage seedGraph() {
        return new SeedStage();
    }

    @Provides
    GraphDumpWriter graphDumpWriter(
            final Filer filer,
            final Diagnostics diagnostics,
            final ProcessorOptions processorOptions,
            final DotRenderer dotRenderer) {
        return new GraphDumpWriter(filer, diagnostics, processorOptions, dotRenderer);
    }

    @Provides
    DumpGraphStage dumpGraph(final GraphDumpWriter writer) {
        return new DumpGraphStage(writer);
    }

    public static ExpandStage assembleExpansionPipeline(
            final List<ExpansionStrategy> strategies,
            final ResolveCtx resolveCtx,
            final NullabilityResolver nullabilityResolver) {
        final var groupsPhase = ExpandGroupsPhase.create(strategies, resolveCtx, nullabilityResolver);
        final List<ExpansionPhase> phases = List.of(groupsPhase);
        return new ExpandStage(phases);
    }

    @Provides
    ExpandStage expandStage(
            final List<ExpansionStrategy> strategies,
            final ResolveCtx resolveCtx,
            final NullabilityResolver nullabilityResolver) {
        return assembleExpansionPipeline(strategies, resolveCtx, nullabilityResolver);
    }

    @Provides
    DumpFullGraphStage dumpFullGraph(final GraphDumpWriter writer) {
        return new DumpFullGraphStage(writer);
    }

    @Provides
    DumpTransformsStage dumpTransforms(final GraphDumpWriter writer) {
        return new DumpTransformsStage(writer);
    }

    @Provides
    DumpPlanStage dumpPlan(final GraphDumpWriter writer) {
        return new DumpPlanStage(writer);
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
            final DiscoverAbstractMethodsStage discoverAbstractMethods,
            final DiscoverMappingsStage discoverMappings,
            final DiscoverCallableMethodsStage discoverCallableMethods) {
        return List.of(discoverAbstractMethods, discoverMappings, discoverCallableMethods);
    }

    @Provides
    @SuppressWarnings("PMD.ExcessiveParameterList")
    static List<Stage> stages(
            @Named("discover") final List<Stage> discoverStages,
            final ValidateNoDuplicateTargetsStage validateNoDuplicateTargets,
            final ValidateSourceParametersStage validateSourceParameters,
            final SeedStage seedGraph,
            final DumpGraphStage dumpGraph,
            final ExpandStage expandStage,
            final DumpFullGraphStage dumpFullGraph,
            final DumpTransformsStage dumpTransforms,
            final DumpPlanStage dumpPlan,
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

    /**
     * The single {@link ExpansionStrategy} list, loaded once and tried as one round each pass (no kind-ordering).
     * Ordered by {@link ExpansionStrategy#priority()} then FQN for deterministic, stable expansion.
     */
    @Singleton
    @Provides
    static List<ExpansionStrategy> expansionStrategies() {
        return StreamSupport.stream(
                        ServiceLoader.load(ExpansionStrategy.class, ProcessorModule.class.getClassLoader())
                                .spliterator(),
                        false)
                .sorted(java.util.Comparator.comparingInt(ExpansionStrategy::priority)
                        .thenComparing(strategy -> strategy.getClass().getName()))
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
