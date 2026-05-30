package io.github.joke.percolate.spi;

import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;

/**
 * Base for a presence container (Optional, Mono). Like {@link SequenceContainer} the developer supplies only
 * {@link #matches}, {@link #element}, and the snippet methods ({@link WrapperCodegen}); the base derives
 * candidacy and emits the unwrap ({@code ENTERING}), collect ({@code EXITING}), and single-element wrap
 * ({@code PRESERVING}) {@link BridgeStep}s. The provider steps carry this container as their codegen handle;
 * the wrap step carries a scalar {@link EdgeCodegen} built from {@link #wrap(com.palantir.javapoet.CodeBlock)}.
 */
public abstract class WrapperContainer implements Bridge, WrapperCodegen {

    protected abstract boolean matches(TypeMirror type, ResolveCtx ctx);

    protected abstract TypeMirror element(TypeMirror type);

    /**
     * The wrapper type over {@code element} (e.g. {@code Optional<element>}). Used to synthesise the unwrap
     * input from a scalar target, or empty when the wrapper cannot wrap that element here.
     */
    protected abstract Optional<TypeMirror> wrapped(TypeMirror element, ResolveCtx ctx);

    public WrapperCodegen streamCodegen() {
        return this;
    }

    public Optional<LoopContainerCodegen> loopCodegen() {
        return Optional.empty();
    }

    /**
     * Unlike a sequence (which iterates an <em>existing</em> source), a wrapper's unwrap is offered for a scalar
     * target by synthesising the wrapper type ({@code Optional<to>}) as its input — so a wrapped source can be
     * reached even when no wrapper node exists yet. When {@code to} is itself the wrapper, the base emits the
     * collect ({@code EXITING}) and single-element wrap ({@code PRESERVING}) steps instead.
     */
    @Override
    public final Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        final var steps = Stream.<BridgeStep>builder();
        if (matches(to, ctx)) {
            final var elementType = element(to);
            steps.add(new BridgeStep(elementType, to, Weights.CONTAINER, this, ScopeTransition.EXITING, "element"));
            final EdgeCodegen wrap = (vars, inputs) -> wrap(inputs.single());
            steps.add(new BridgeStep(elementType, to, Weights.CONTAINER, wrap));
        } else {
            wrapped(to, ctx)
                    .ifPresent(wrapperType -> steps.add(new BridgeStep(
                            wrapperType, to, Weights.CONTAINER, this, ScopeTransition.ENTERING, "element")));
        }
        return steps.build();
    }
}
