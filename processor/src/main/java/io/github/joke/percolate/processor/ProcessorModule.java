package io.github.joke.percolate.processor;

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
import io.github.joke.percolate.processor.internal.stages.validate.ValidateNoDuplicateTargetsStage;
import io.github.joke.percolate.processor.internal.stages.validate.ValidateOptionConsumptionStage;
import io.github.joke.percolate.processor.internal.stages.validate.ValidateSourceParametersStage;
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver;
import io.github.joke.percolate.processor.nullability.NullabilityAnnotations;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.DirectiveReader;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.SourceProjection;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.processor.nullability.NullabilityAnnotations.jspecifyDefaults;
import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;
import static java.util.ServiceLoader.load;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static java.util.stream.Stream.concat;
import static java.util.stream.StreamSupport.stream;

// Dagger requires every @Provides method on a @Module to be static, so the whole class is framework-mandated
// statics. Suppressed here rather than five times over, which would itself trip AvoidDuplicateLiterals.
@SuppressWarnings("PMD.StaticMethodsModifyStaticState")
@Module
@RequiredArgsConstructor(onConstructor_ = @Inject)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class ProcessorModule {

    private final ProcessingEnvironment processingEnvironment;

    @VisibleForTesting
    @Provides
    Elements elements() {
        return processingEnvironment.getElementUtils();
    }

    @VisibleForTesting
    @Provides
    Types types() {
        return processingEnvironment.getTypeUtils();
    }

    @VisibleForTesting
    @Provides
    Messager messager() {
        return processingEnvironment.getMessager();
    }

    @VisibleForTesting
    @Provides
    Filer filer() {
        return processingEnvironment.getFiler();
    }

    @VisibleForTesting
    @Provides
    ProcessorOptions processorOptions(final ProcessorOptionsReader reader) {
        return reader.from(processingEnvironment.getOptions());
    }

    // The target SourceVersion, read once from the environment — the enum-conversion strategy's codegen resolves
    // switch.style's AUTO against it; the engine itself reads no version.
    @VisibleForTesting
    @Provides
    SourceVersion sourceVersion() {
        return processingEnvironment.getSourceVersion();
    }

    @VisibleForTesting
    @Provides
    @Singleton
    NullabilityAnnotations nullabilityAnnotations(final ProcessorOptions processorOptions) {
        final var defaults = jspecifyDefaults();
        final var custom = processorOptions.getCustomNullableAnnotations();
        if (custom.isEmpty()) {
            return defaults;
        }
        final var merged =
                concat(defaults.getNullableFqns().stream(), custom.stream()).collect(toUnmodifiableSet());
        return new NullabilityAnnotations(merged, defaults.getMarkedFqns(), defaults.getUnmarkedFqns());
    }

    @VisibleForTesting
    @Provides
    @Singleton
    NullabilityResolver nullabilityResolver(final JspecifyNullabilityResolver resolver) {
        return resolver;
    }

    @VisibleForTesting
    @Provides
    ExpandStage expandStage(
            final List<ExpansionStrategy> strategies,
            final List<SourceProjection> projections,
            final Types types,
            final Elements elements,
            final NullabilityResolver nullabilityResolver,
            final ProcessorOptions options) {
        return new ExpandStage(strategies, projections, types, elements, nullabilityResolver, options);
    }

    @VisibleForTesting
    @Provides
    @Named("discover")
    static List<Stage> discoverStages(
            final DiscoverAbstractMethodsStage discoverAbstractMethods,
            final DiscoverMappingsStage discoverMappings,
            final DiscoverCallableMethodsStage discoverCallableMethods) {
        return List.of(discoverAbstractMethods, discoverMappings, discoverCallableMethods);
    }

    @VisibleForTesting
    @Provides
    @SuppressWarnings("PMD.ExcessiveParameterList")
    static List<Stage> stages(
            @Named("discover") final List<Stage> discoverStages,
            final ValidateNoDuplicateTargetsStage validateNoDuplicateTargets,
            final ValidateSourceParametersStage validateSourceParameters,
            final ExpandStage expandStage,
            final DumpFullGraphStage dumpFullGraph,
            final DumpTransformsStage dumpTransforms,
            final DumpPlanStage dumpPlan,
            final ValidateOptionConsumptionStage validateOptionConsumption,
            final RealisationDiagnosticsStage realisationDiagnostics,
            final GenerateStage generateStage) {
        return concat(
                        discoverStages.stream(),
                        Stream.<Stage>of(
                                validateNoDuplicateTargets,
                                validateSourceParameters,
                                expandStage,
                                // Realisation outcome is computed before the Filer-writing stages (dumps,
                                // generate) so they can skip a deferred round and write each artifact once.
                                validateOptionConsumption,
                                realisationDiagnostics,
                                dumpFullGraph,
                                dumpTransforms,
                                dumpPlan,
                                generateStage))
                .collect(toUnmodifiableList());
    }

    // The single ExpansionStrategy list, loaded once and tried as one round each pass (no kind-ordering). Ordered
    // by ExpansionStrategy.priority() then FQN for deterministic, stable expansion.
    @VisibleForTesting
    @Singleton
    @Provides
    static List<ExpansionStrategy> expansionStrategies() {
        return stream(
                        load(ExpansionStrategy.class, ProcessorModule.class.getClassLoader())
                                .spliterator(),
                        false)
                .sorted(comparingInt(ExpansionStrategy::priority)
                        .thenComparing(strategy -> strategy.getClass().getName()))
                .collect(toUnmodifiableList());
    }

    // The DirectiveReader list (design D7 of change decouple-engine-from-strategy-semantics), loaded once via
    // ServiceLoader exactly as ExpansionStrategy is; ordered by FQN for deterministic discovery.
    @VisibleForTesting
    @Singleton
    @Provides
    static List<DirectiveReader> directiveReaders() {
        return stream(
                        load(DirectiveReader.class, ProcessorModule.class.getClassLoader())
                                .spliterator(),
                        false)
                .sorted(comparing(reader -> reader.getClass().getName()))
                .collect(toUnmodifiableList());
    }

    // The SourceProjection list (design D8), loaded once. Source-facing projectors the driver consults to widen
    // grounding-by-match's match set; ordered by FQN for deterministic expansion.
    @VisibleForTesting
    @Singleton
    @Provides
    static List<SourceProjection> sourceProjections() {
        return stream(
                        load(SourceProjection.class, ProcessorModule.class.getClassLoader())
                                .spliterator(),
                        false)
                .sorted(comparing(projection -> projection.getClass().getName()))
                .collect(toUnmodifiableList());
    }
}
