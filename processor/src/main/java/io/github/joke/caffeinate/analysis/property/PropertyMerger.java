package io.github.joke.caffeinate.analysis.property;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PropertyMerger {

    private PropertyMerger() {}

    public static List<Property> merge(
            Set<PropertyDiscoveryStrategy> strategies,
            TypeElement type,
            ProcessingEnvironment env) {
        Map<String, Property> byName = new LinkedHashMap<>();
        // Fields first (lower priority)
        for (PropertyDiscoveryStrategy strategy : strategies) {
            for (Property p : strategy.discover(type, env)) {
                if (p.getAccessor().getKind() == ElementKind.FIELD) {
                    byName.putIfAbsent(p.getName(), p);
                }
            }
        }
        // Getters second — overwrite fields of same name
        for (PropertyDiscoveryStrategy strategy : strategies) {
            for (Property p : strategy.discover(type, env)) {
                if (p.getAccessor().getKind() == ElementKind.METHOD) {
                    byName.put(p.getName(), p);
                }
            }
        }
        return new ArrayList<>(byName.values());
    }
}
