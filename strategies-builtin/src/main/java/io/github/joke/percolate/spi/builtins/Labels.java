package io.github.joke.percolate.spi.builtins;

import static java.util.stream.Collectors.joining;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Composes the human-readable, fully-typed {@code label} a built-in strategy attaches to its {@link
 * io.github.joke.percolate.spi.OperationSpec} (the operation's debug-graph identity). Type names are reduced to
 * their simple form recursively (generic arguments included); a conversion reads as {@code from→to} with the glyph
 * arrow. Best-effort for a debug label — never the basis of a behavioural decision.
 */
final class Labels {

    static final String ARROW = "→";

    private Labels() {}

    /** The simple name of {@code type}, recursing into generic arguments (e.g. {@code Optional<Set<Address>>}). */
    static String simple(final TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return type.toString();
        }
        final var declared = (DeclaredType) type;
        final var name = declared.asElement().getSimpleName().toString();
        final var args = declared.getTypeArguments();
        if (args.isEmpty()) {
            return name;
        }
        return name + '<' + args.stream().map(Labels::simple).collect(joining(", ")) + '>';
    }

    /** A conversion label {@code from→to} (e.g. {@code int→long}, {@code int→Integer}). */
    static String conversion(final TypeMirror from, final TypeMirror to) {
        return simple(from) + ARROW + simple(to);
    }
}
