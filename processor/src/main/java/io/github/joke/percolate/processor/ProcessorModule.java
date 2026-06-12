package io.github.joke.percolate.processor;

import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

import dagger.Module;
import dagger.Provides;
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
import io.github.joke.percolate.processor.stages.expand.ExpandGroupsPhase;
import io.github.joke.percolate.processor.stages.expand.ExpandStage;
import io.github.joke.percolate.processor.stages.generate.GenerateStage;
import io.github.joke.percolate.processor.stages.seed.SeedStage;
import io.github.joke.percolate.processor.stages.validate.RealisationDiagnosticsStage;
import io.github.joke.percolate.processor.stages.validate.ValidateConstantDefaultLegalityStage;
import io.github.joke.percolate.processor.stages.validate.ValidateMappingShapeStage;
import io.github.joke.percolate.processor.stages.validate.ValidateNoDuplicateTargetsStage;
import io.github.joke.percolate.processor.stages.validate.ValidateSourceParametersStage;
import io.github.joke.percolate.spi.CallableMethods;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.ResolveCtx;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;
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
        final var merged = Stream.concat(defaults.getNullableFqns().stream(), custom.stream())
                .collect(toUnmodifiableSet());
        return new NullabilityAnnotations(merged, defaults.getMarkedFqns(), defaults.getUnmarkedFqns());
    }

    @Provides
    @Singleton
    NullabilityResolver nullabilityResolver(final JspecifyNullabilityResolver resolver) {
        return resolver;
    }

    public static ExpandStage assembleExpansionPipeline(
            final List<ExpansionStrategy> strategies,
            final ResolveCtx resolveCtx,
            final NullabilityResolver nullabilityResolver) {
        final var groupsPhase = ExpandGroupsPhase.create(strategies, resolveCtx, nullabilityResolver);
        return new ExpandStage(List.of(groupsPhase));
    }

    @Provides
    ExpandStage expandStage(
            final List<ExpansionStrategy> strategies,
            final ResolveCtx resolveCtx,
            final NullabilityResolver nullabilityResolver) {
        return assembleExpansionPipeline(strategies, resolveCtx, nullabilityResolver);
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
            final ValidateMappingShapeStage validateMappingShape,
            final ValidateSourceParametersStage validateSourceParameters,
            final SeedStage seedStage,
            final DumpGraphStage dumpGraph,
            final ExpandStage expandStage,
            final DumpFullGraphStage dumpFullGraph,
            final DumpTransformsStage dumpTransforms,
            final DumpPlanStage dumpPlan,
            final ValidateConstantDefaultLegalityStage validateConstantDefaultLegality,
            final RealisationDiagnosticsStage realisationDiagnostics,
            final GenerateStage generateStage) {
        return Stream.concat(
                        discoverStages.stream(),
                        Stream.<Stage>of(
                                validateNoDuplicateTargets,
                                validateMappingShape,
                                validateSourceParameters,
                                seedStage,
                                dumpGraph,
                                expandStage,
                                dumpFullGraph,
                                dumpTransforms,
                                dumpPlan,
                                validateConstantDefaultLegality,
                                realisationDiagnostics,
                                generateStage))
                .collect(toUnmodifiableList());
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
                .sorted(Comparator.comparingInt(ExpansionStrategy::priority)
                        .thenComparing(strategy -> strategy.getClass().getName()))
                .collect(toUnmodifiableList());
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
