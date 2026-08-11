package io.github.joke.percolate.processor.internal.graph;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import static java.util.stream.Collectors.joining;

// A method scope's identity is its .method alone (design D5 of change decouple-engine-from-strategy-semantics)
// — two instances for the same method are the same scope regardless of which InputDecls they were built with,
// so scope dedup (used pervasively as a map key) is unaffected by how a caller constructed it. MethodScope
// itself reads no annotation and holds no resolver: its declarations arrive already resolved, built by whoever
// seeds the method's scope.
@Value
public class MethodScope implements Scope {

    ExecutableElement method;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    List<InputDecl> declarations;

    // A map-key-only scope carrying no input declarations; never call .inputDecls() on this instance.
    public MethodScope(final ExecutableElement method) {
        this(method, List.of());
    }

    // The scope actually used during expansion: declarations is one resolved InputDecl per parameter.
    public MethodScope(final ExecutableElement method, final List<InputDecl> declarations) {
        this.method = method;
        this.declarations = List.copyOf(declarations);
    }

    @Override
    public String encode() {
        final var name = method.getSimpleName().toString();
        final var paramTypes =
                method.getParameters().stream().map(p -> p.asType().toString()).collect(joining(","));
        return name + "(" + paramTypes + ")";
    }

    @Override
    public Optional<Scope> parent() {
        return Optional.of(MapperScope.INSTANCE);
    }

    @Override
    public Stream<InputDecl> inputDecls() {
        return declarations.stream();
    }
}
