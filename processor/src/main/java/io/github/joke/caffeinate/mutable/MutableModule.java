package io.github.joke.caffeinate.mutable;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoSet;
import dagger.multibindings.Multibinds;
import io.github.joke.caffeinate.immutable.AnalysisPhase;
import io.github.joke.caffeinate.immutable.GenerationPhase;
import io.github.joke.caffeinate.immutable.ValidationPhase;
import io.github.joke.caffeinate.strategy.ClassStructureStrategy;
import io.github.joke.caffeinate.strategy.GenerationStrategy;
import io.github.joke.caffeinate.strategy.GetterStrategy;
import java.util.Set;

@Module
public interface MutableModule {

    @Multibinds
    @AnalysisPhase
    Set<GenerationStrategy> analysisStrategies();

    @Multibinds
    @ValidationPhase
    Set<GenerationStrategy> validationStrategies();

    @Binds
    @IntoSet
    @AnalysisPhase
    GenerationStrategy mutablePropertyDiscovery(MutablePropertyDiscoveryStrategy impl);

    @Binds
    @IntoSet
    @ValidationPhase
    GenerationStrategy setterValidation(SetterValidationStrategy impl);

    @Binds
    @IntoSet
    @GenerationPhase
    GenerationStrategy classStructure(ClassStructureStrategy impl);

    @Binds
    @IntoSet
    @GenerationPhase
    GenerationStrategy mutableField(MutableFieldStrategy impl);

    @Binds
    @IntoSet
    @GenerationPhase
    GenerationStrategy getter(GetterStrategy impl);

    @Binds
    @IntoSet
    @GenerationPhase
    GenerationStrategy setter(SetterStrategy impl);

    @Binds
    @IntoSet
    @GenerationPhase
    GenerationStrategy mutableConstructor(MutableConstructorStrategy impl);
}
