package io.github.joke.percolate.spi;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;

/**
 * Base for a presence container (Optional, Mono). Like {@link SequenceContainer} the developer supplies only
 * {@link #matches}, {@link #element}, and the snippet methods ({@link WrapperCodegen}); the base derives candidacy
 * and emits the unwrap ({@code ENTERING} element-scope) and single-element wrap (scalar) {@link ExpansionStep}s.
 * The unwrap step carries this container as its codegen handle; the wrap step carries a scalar {@link EdgeCodegen}
 * built from {@link #wrap(com.palantir.javapoet.CodeBlock)}.
 *
 * <p>A presence wrapper has <b>no collect step</b>: {@code collect} is a sequence terminal (close a stream into a
 * container), which is meaningless for a 0-or-1 presence container. Only sequences collect.
 */
public abstract class WrapperContainer implements ContainerMatch, WrapperCodegen {

    private static final String ELEMENT_ROLE = "element";

    protected abstract boolean matches(TypeMirror type, ResolveCtx ctx);

    protected abstract TypeMirror element(TypeMirror type);

    /**
     * The wrapper type over {@code element} (e.g. {@code Optional<element>}). Used to synthesise the unwrap input
     * from a scalar target, or empty when the wrapper cannot wrap that element here.
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
     * reached even when no wrapper node exists yet. When {@code to} is itself the wrapper, the base emits only the
     * single-element wrap (scalar) step; a wrapper never emits a collect step.
     */
    @Override
    public final Stream<ExpansionStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        final var steps = Stream.<ExpansionStep>builder();
        if (matches(to, ctx)) {
            final var elementType = element(to);
            final EdgeCodegen wrap = (vars, inputs) -> wrap(inputs.single());
            final var elementSlot = new Slot(ELEMENT_ROLE, elementType, Weights.CONTAINER, null);
            steps.add(ExpansionStep.boundary(List.of(elementSlot), to, wrap, Weights.CONTAINER));
        } else {
            wrapped(to, ctx).ifPresent(wrapperType -> {
                final var wrapperSlot = new Slot(ELEMENT_ROLE, wrapperType, Weights.CONTAINER, null);
                steps.add(ExpansionStep.containerBoundary(
                        wrapperSlot, to, this, ElementScope.ENTERING, Weights.CONTAINER));
            });
        }
        return steps.build();
    }
}
