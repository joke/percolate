package io.github.joke.percolate.spi;

import io.github.joke.percolate.spi.types.TypeRef;
import javax.lang.model.type.TypeMirror;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * The child-scope declaration of a scope-owning {@link OperationSpec} (a container element mapping): the element
 * types and nullness of the child plan's param-root (element in) and return-root (element out). The driver mints
 * these two roots inside the freshly created child scope when the operation lands; no dependency edge crosses the
 * scope boundary — the owning operation is the only coupling.
 *
 * <p>A child scope may <b>reference the same type variable</b> as its owning operation's type-variable port (design
 * D2/D3). When {@link #elementInTemplate}/{@link #elementOutTemplate} is present, that element type is grounded from
 * the port's match (the {@link #elementIn}/{@link #elementOut} field then holds only a representative shape that
 * grounding replaces). The functor-lift {@code F<B> ← F<A>} declares its child as {@code A → B}: {@code elementOut}
 * is concrete ({@code B}, from the target) and {@code elementInTemplate} is the variable {@code A}.
 */
@Value
@AllArgsConstructor
public class ChildScopeSpec {
    TypeMirror elementIn;
    Nullability elementInNullness;
    TypeMirror elementOut;
    Nullability elementOutNullness;

    /** The variable shape of the element-in type, or {@code null} when it is concrete. */
    @Nullable
    TypeRef elementInTemplate;

    /** The variable shape of the element-out type, or {@code null} when it is concrete. */
    @Nullable
    TypeRef elementOutTemplate;

    /** A concrete child scope (both element types fully known). */
    public ChildScopeSpec(
            final TypeMirror elementIn,
            final Nullability elementInNullness,
            final TypeMirror elementOut,
            final Nullability elementOutNullness) {
        this(elementIn, elementInNullness, elementOut, elementOutNullness, null, null);
    }

    /**
     * A functor-lift child {@code A → B}: {@code elementInTemplate} is the variable {@code A} (grounded by the
     * owning port's match), {@code elementOut} is the concrete {@code B}. The {@code elementIn} field is seeded with
     * {@code elementOut} as a representative placeholder; grounding overwrites it with the matched element type.
     */
    public static ChildScopeSpec lifted(
            final TypeRef elementInTemplate,
            final Nullability elementInNullness,
            final TypeMirror elementOut,
            final Nullability elementOutNullness) {
        return new ChildScopeSpec(
                elementOut, elementInNullness, elementOut, elementOutNullness, elementInTemplate, null);
    }
}
