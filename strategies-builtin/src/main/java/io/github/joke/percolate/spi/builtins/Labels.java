package io.github.joke.percolate.spi.builtins;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.experimental.UtilityClass;

import static java.util.stream.Collectors.joining;

// Composes the human-readable, fully-typed label a built-in strategy attaches to its
// io.github.joke.percolate.spi.OperationSpec (the operation's debug-graph identity). Type names are reduced to
// their simple form recursively (generic arguments included); a conversion reads as from→to with the glyph
// arrow. Best-effort for a debug label — never the basis of a behavioural decision.
@UtilityClass
public class Labels {

    public static final String ARROW = "→";

    // The simple name of type, recursing into generic arguments (e.g. Optional<Set<Address>>).
    public static String simple(final TypeMirror type) {
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

    // A conversion label from→to (e.g. int→long, int→Integer).
    public static String conversion(final TypeMirror from, final TypeMirror to) {
        return simple(from) + ARROW + simple(to);
    }
}
