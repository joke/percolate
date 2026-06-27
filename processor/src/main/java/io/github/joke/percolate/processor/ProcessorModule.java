package io.github.joke.percolate.processor;

import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

import dagger.Module;
import dagger.Provides;
import io.github.joke.percolate.processor.internal.stages.Stage;
import io.github.joke.percolate.processor.internal.stages.discover.DiscoverAbstractMethodsStage;
import io.github.joke.percolate.processor.internal.stages.discover.DiscoverCallableMethodsStage;
import io.github.joke.percolate.processor.internal.stages.discover.DiscoverMappingsStage;
import io.github.joke.percolate.processor.internal.stages.dump.DumpFullGraphStage;
import io.github.joke.percolate.processor.internal.stages.dump.DumpPlanStage;
import io.github.joke.percolate.processor.internal.stages.dump.DumpTransformsStage;
import io.github.joke.percolate.processor.internal.stages.expand.ExpandStage;
import io.github.joke.percolate.processor.internal.stages.generate.GenerateStage;
import io.github.joke.percolate.processor.internal.stages.validate.RealisationDiagnosticsStage;
import io.github.joke.percolate.processor.internal.stages.validate.ValidateConstantDefaultLegalityStage;
import io.github.joke.percolate.processor.internal.stages.validate.ValidateMappingShapeStage;
import io.github.joke.percolate.processor.internal.stages.validate.ValidateNoDuplicateTargetsStage;
import io.github.joke.percolate.processor.internal.stages.validate.ValidateSourceParametersStage;
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver;
import io.github.joke.percolate.processor.nullability.NullabilityAnnotations;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.SourceProjection;
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
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@Module
@RequiredArgsConstructor(onConstructor_ = @Inject)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class ProcessorModule {

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
            final List<SourceProjection> projections,
            final Types types,
            final Elements elements,
            final NullabilityResolver nullabilityResolver) {
        return new ExpandStage(strategies, projections, types, elements, nullabilityResolver);
    }

    @Provides
    ExpandStage expandStage(
            final List<ExpansionStrategy> strategies,
            final List<SourceProjection> projections,
            final Types types,
            final Elements elements,
            final NullabilityResolver nullabilityResolver) {
        return assembleExpansionPipeline(strategies, projections, types, elements, nullabilityResolver);
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
                                expandStage,
                                // Realisation outcome is computed before the Filer-writing stages (dumps,
                                // generate) so they can skip a deferred round and write each artifact once.
                                validateConstantDefaultLegality,
                                realisationDiagnostics,
                                dumpFullGraph,
                                dumpTransforms,
                                dumpPlan,
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

    /**
     * The {@link SourceProjection} list (design D8), loaded once. Source-facing projectors the driver consults to
     * widen grounding-by-match's match set; ordered by FQN for deterministic expansion.
     */
    @Singleton
    @Provides
    static List<SourceProjection> sourceProjections() {
        return StreamSupport.stream(
                        ServiceLoader.load(SourceProjection.class, ProcessorModule.class.getClassLoader())
                                .spliterator(),
                        false)
                .sorted(Comparator.comparing(projection -> projection.getClass().getName()))
                .collect(toUnmodifiableList());
    }
}
