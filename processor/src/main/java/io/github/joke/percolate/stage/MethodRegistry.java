package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.joining;

import io.github.joke.percolate.model.MethodDefinition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

public final class MethodRegistry {
    private final Map<TypePair, RegistryEntry> entries = new LinkedHashMap<>();

    static String keyFor(MethodDefinition method) {
        if (method.getParameters().size() == 1) {
            return method.getParameters().get(0).getType().toString();
        }
        return "("
                + method.getParameters().stream()
                        .map(p -> p.getType().toString())
                        .collect(joining(","))
                + ")";
    }

    public void register(MethodDefinition method, RegistryEntry entry) {
        register(keyFor(method), method.getReturnType().toString(), entry);
    }

    public Optional<RegistryEntry> lookup(MethodDefinition method) {
        return lookup(keyFor(method), method.getReturnType().toString());
    }

    public Optional<RegistryEntry> lookup(TypeMirror in, TypeMirror out) {
        return lookup(in.toString(), out.toString());
    }

    public Optional<RegistryEntry> lookup(String inTypeName, String outTypeName) {
        return Optional.ofNullable(entries.get(new TypePair(inTypeName, outTypeName)));
    }

    public void register(TypeMirror in, TypeMirror out, RegistryEntry entry) {
        register(in.toString(), out.toString(), entry);
    }

    public void register(String inTypeName, String outTypeName, RegistryEntry entry) {
        entries.put(new TypePair(inTypeName, outTypeName), entry);
    }

    public Map<TypePair, RegistryEntry> entries() {
        return Collections.unmodifiableMap(entries);
    }
}
