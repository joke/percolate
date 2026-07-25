package io.github.joke.percolate.processor.internal.graph;

import io.github.joke.percolate.spi.Nullability;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

@Value
public class MethodScope implements Scope {
    ExecutableElement method;

    @Override
    public String encode() {
        final var name = method.getSimpleName().toString();
        final var paramTypes = method.getParameters().stream()
                .map(p -> p.asType().toString())
                .collect(java.util.stream.Collectors.joining(","));
        return name + "(" + paramTypes + ")";
    }

    @Override
    public Optional<Scope> parent() {
        return Optional.of(MapperScope.INSTANCE);
    }

    /** One input declaration per method parameter — a single-segment {@code SourceLocation} typed from the signature. */
    @Override
    public Stream<InputDecl> inputDecls(final BiFunction<TypeMirror, Element, Nullability> nullness) {
        return method.getParameters().stream()
                .map(param -> new InputDecl(
                        new SourceLocation(AccessPath.of(param.getSimpleName().toString())),
                        param.asType(),
                        nullness.apply(param.asType(), param)));
    }

    /**
     * One ambient entry per {@code @Ambient} parameter, keyed per {@link AmbientKeys#keyOf}, at the same
     * single-segment {@code SourceLocation} an ordinary {@code @Map} source would resolve to — so both paths
     * materialise the identical graph {@link Value} for that parameter.
     */
    @Override
    public Stream<AmbientDecl> ambientDecls(final BiFunction<TypeMirror, Element, Nullability> nullness) {
        return method.getParameters().stream().flatMap(param -> ambientDecl(param, nullness).stream());
    }

    private Optional<AmbientDecl> ambientDecl(
            final VariableElement param, final BiFunction<TypeMirror, Element, Nullability> nullness) {
        final var key = AmbientKeys.keyOf(param);
        if (key == null) {
            return Optional.empty();
        }
        return Optional.of(new AmbientDecl(
                key,
                new SourceLocation(AccessPath.of(param.getSimpleName().toString())),
                param.asType(),
                nullness.apply(param.asType(), param)));
    }
}
