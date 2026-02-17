package io.github.joke.caffeinate.immutable;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoSet;
import dagger.multibindings.Multibinds;
import io.github.joke.caffeinate.phase.AnalysisPhase;
import io.github.joke.caffeinate.phase.GenerationPhase;
import io.github.joke.caffeinate.strategy.ClassStructureStrategy;
import io.github.joke.caffeinate.strategy.ConstructorStrategy;
import io.github.joke.caffeinate.strategy.FieldStrategy;
import io.github.joke.caffeinate.strategy.GenerationStrategy;
import io.github.joke.caffeinate.strategy.GetterStrategy;
import io.github.joke.caffeinate.strategy.PropertyDiscoveryStrategy;
import java.util.Set;

@Module
public interface ImmutableModule {

    @Multibinds
    @AnalysisPhase
    Set<GenerationStrategy> analysisStrategies();

    @Binds
    @IntoSet
    @AnalysisPhase
    GenerationStrategy propertyDiscovery(PropertyDiscoveryStrategy impl);

    @Binds
    @IntoSet
    @GenerationPhase
    GenerationStrategy classStructure(ClassStructureStrategy impl);

    @Binds
    @IntoSet
    @GenerationPhase
    GenerationStrategy field(FieldStrategy impl);

    @Binds
    @IntoSet
    @GenerationPhase
    GenerationStrategy getter(GetterStrategy impl);

    @Binds
    @IntoSet
    @GenerationPhase
    GenerationStrategy constructor(ConstructorStrategy impl);
}
