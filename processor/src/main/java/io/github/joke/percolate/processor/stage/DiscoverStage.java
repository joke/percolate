package io.github.joke.percolate.processor.stage;

import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.model.DiscoveredMethod;
import io.github.joke.percolate.processor.model.DiscoveredModel;
import io.github.joke.percolate.processor.model.MapperModel;
import io.github.joke.percolate.processor.model.MappingMethodModel;
import io.github.joke.percolate.processor.model.ReadAccessor;
import io.github.joke.percolate.processor.model.WriteAccessor;
import io.github.joke.percolate.processor.spi.SourcePropertyDiscovery;
import io.github.joke.percolate.processor.spi.TargetPropertyDiscovery;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public final class DiscoverStage {

    private final Elements elements;
    private final Types types;
    private final List<SourcePropertyDiscovery> sourceStrategies;
    private final List<TargetPropertyDiscovery> targetStrategies;

    @Inject
    DiscoverStage(final Elements elements, final Types types) {
        this.elements = elements;
        this.types = types;
        this.sourceStrategies = loadAndSort(SourcePropertyDiscovery.class);
        this.targetStrategies = loadAndSort(TargetPropertyDiscovery.class);
    }

    public StageResult<DiscoveredModel> execute(final MapperModel mapperModel) {
        final List<DiscoveredMethod> discoveredMethods = new ArrayList<>();

        for (final MappingMethodModel method : mapperModel.getMethods()) {
            final Map<String, ReadAccessor> sourceProps = discoverSourceProperties(method.getSourceType());
            final Map<String, WriteAccessor> targetProps = discoverTargetProperties(method.getTargetType());
            discoveredMethods.add(new DiscoveredMethod(method, sourceProps, targetProps));
        }

        return StageResult.success(new DiscoveredModel(mapperModel.getMapperType(), discoveredMethods));
    }

    private Map<String, ReadAccessor> discoverSourceProperties(final TypeMirror sourceType) {
        final Map<String, ReadAccessor> merged = new LinkedHashMap<>();
        final Map<String, Integer> priorities = new LinkedHashMap<>();

        for (final SourcePropertyDiscovery strategy : sourceStrategies) {
            for (final ReadAccessor accessor : strategy.discover(sourceType, elements, types)) {
                final int currentPriority = priorities.getOrDefault(accessor.name(), Integer.MIN_VALUE);
                if (strategy.priority() > currentPriority) {
                    merged.put(accessor.name(), accessor);
                    priorities.put(accessor.name(), strategy.priority());
                }
            }
        }

        return merged;
    }

    private Map<String, WriteAccessor> discoverTargetProperties(final TypeMirror targetType) {
        final Map<String, WriteAccessor> merged = new LinkedHashMap<>();
        final Map<String, Integer> priorities = new LinkedHashMap<>();

        for (final TargetPropertyDiscovery strategy : targetStrategies) {
            for (final WriteAccessor accessor : strategy.discover(targetType, elements, types)) {
                final int currentPriority = priorities.getOrDefault(accessor.name(), Integer.MIN_VALUE);
                if (strategy.priority() > currentPriority) {
                    merged.put(accessor.name(), accessor);
                    priorities.put(accessor.name(), strategy.priority());
                }
            }
        }

        return merged;
    }

    private static <T> List<T> loadAndSort(final Class<T> serviceClass) {
        final List<T> strategies = new ArrayList<>();
        ServiceLoader.load(serviceClass, DiscoverStage.class.getClassLoader()).forEach(strategies::add);
        strategies.sort(Comparator.comparingInt(s -> {
            if (s instanceof SourcePropertyDiscovery) {
                return -((SourcePropertyDiscovery) s).priority();
            }
            if (s instanceof TargetPropertyDiscovery) {
                return -((TargetPropertyDiscovery) s).priority();
            }
            return 0;
        }));
        return strategies;
    }
}
