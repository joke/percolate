package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.joining;

import io.github.joke.percolate.model.MethodDefinition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import org.jetbrains.annotations.Unmodifiable;

public final class MethodRegistry {
    private final Map<TypePair, RegistryEntry> entries = new LinkedHashMap<>();

    static String keyFor(final MethodDefinition method) {
        if (method.getParameters().size() == 1) {
            return method.getParameters().get(0).getType().toString();
        }
        return "("
                + method.getParameters().stream()
                        .map(parameter -> parameter.getType().toString())
                        .collect(joining(","))
                + ")";
    }

    public void register(final MethodDefinition method, final RegistryEntry entry) {
        register(keyFor(method), method.getReturnType().toString(), entry);
    }

    public Optional<RegistryEntry> lookup(final MethodDefinition method) {
        return lookup(keyFor(method), method.getReturnType().toString());
    }

    public Optional<RegistryEntry> lookup(final TypeMirror in, final TypeMirror out) {
        return lookup(in.toString(), out.toString());
    }

    public Optional<RegistryEntry> lookup(final String inTypeName, final String outTypeName) {
        return Optional.ofNullable(entries.get(new TypePair(inTypeName, outTypeName)));
    }

    public void register(final TypeMirror in, final TypeMirror out, final RegistryEntry entry) {
        register(in.toString(), out.toString(), entry);
    }

    public void register(final String inTypeName, final String outTypeName, final RegistryEntry entry) {
        entries.put(new TypePair(inTypeName, outTypeName), entry);
    }

    public @Unmodifiable Map<TypePair, RegistryEntry> entries() {
        return Collections.unmodifiableMap(entries);
    }
}
