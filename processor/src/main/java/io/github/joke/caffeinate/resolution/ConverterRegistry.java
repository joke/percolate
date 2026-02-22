package io.github.joke.caffeinate.resolution;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

/**
 * Lookup table: (sourceType, targetType) -> Converter.
 * User-defined MethodConverters are registered first and are never overwritten by strategies.
 */
public final class ConverterRegistry {

    private final Map<String, Converter> converters = new LinkedHashMap<>();

    public ConverterRegistry() {}

    /**
     * Registers a converter for (source -> target). No-op if the pair is already registered.
     * Returns true if a new entry was added, false if already present.
     */
    public boolean register(TypeMirror source, TypeMirror target, Converter converter) {
        return converters.putIfAbsent(key(source, target), converter) == null;
    }

    public Optional<Converter> converterFor(TypeMirror source, TypeMirror target) {
        return Optional.ofNullable(converters.get(key(source, target)));
    }

    public boolean hasConverter(TypeMirror source, TypeMirror target) {
        return converters.containsKey(key(source, target));
    }

    /**
     * Key uses toString() for hashing (TypeMirror has no stable hashCode across implementations).
     */
    private String key(TypeMirror source, TypeMirror target) {
        return source.toString() + " -> " + target.toString();
    }
}
