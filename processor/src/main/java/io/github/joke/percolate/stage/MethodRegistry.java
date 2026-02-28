package io.github.joke.percolate.stage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

public final class MethodRegistry {
    private final Map<TypePair, RegistryEntry> entries = new LinkedHashMap<>();

    public Optional<RegistryEntry> lookup(TypeMirror in, TypeMirror out) {
        return lookup(in.toString(), out.toString());
    }

    public Optional<RegistryEntry> lookup(String inTypeName, String outTypeName) {
        return Optional.ofNullable(entries.get(new TypePair(inTypeName, outTypeName)));
    }

    public void register(TypeMirror in, TypeMirror out, RegistryEntry entry) {
        entries.put(new TypePair(in.toString(), out.toString()), entry);
    }

    public void register(String inTypeName, String outTypeName, RegistryEntry entry) {
        entries.put(new TypePair(inTypeName, outTypeName), entry);
    }

    public Map<TypePair, RegistryEntry> entries() {
        return Collections.unmodifiableMap(entries);
    }
}
