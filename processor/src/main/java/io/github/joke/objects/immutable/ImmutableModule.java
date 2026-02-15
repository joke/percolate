package io.github.joke.objects.immutable;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoSet;
import io.github.joke.objects.strategy.ClassStructureStrategy;
import io.github.joke.objects.strategy.GenerationStrategy;

@Module
public interface ImmutableModule {

    @Binds
    @IntoSet
    GenerationStrategy classStructure(ClassStructureStrategy impl);
}
