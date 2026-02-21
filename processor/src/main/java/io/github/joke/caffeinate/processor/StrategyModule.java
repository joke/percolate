package io.github.joke.caffeinate.processor;

import dagger.Module;
import dagger.Provides;
import io.github.joke.caffeinate.analysis.property.PropertyDiscoveryStrategy;
import io.github.joke.caffeinate.codegen.strategy.TypeMappingStrategy;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Module
public class StrategyModule {

    @Provides
    @ProcessorScoped
    Set<PropertyDiscoveryStrategy> propertyDiscoveryStrategies() {
        return StreamSupport.stream(
                        ServiceLoader.load(PropertyDiscoveryStrategy.class, StrategyModule.class.getClassLoader())
                                .spliterator(),
                        false)
                .collect(Collectors.toSet());
    }

    @Provides
    @ProcessorScoped
    Set<TypeMappingStrategy> typeMappingStrategies() {
        return StreamSupport.stream(
                        ServiceLoader.load(TypeMappingStrategy.class, StrategyModule.class.getClassLoader())
                                .spliterator(),
                        false)
                .collect(Collectors.toSet());
    }
}
